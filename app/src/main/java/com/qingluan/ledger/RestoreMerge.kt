package com.qingluan.ledger

import java.util.UUID
import org.json.JSONObject

internal fun readSyncId(json: JSONObject): String =
    json.optString("syncId", "").also {
        require(
            it.isEmpty() || runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false)
        ) {
            "记录标识无效"
        }
    }

// Legacy records get a deterministic identity, persisted on the first merge. Database IDs may
// subsequently change when independently created records from different devices collide.
internal fun stableSyncId(value: String, legacy: String): String =
    value.ifEmpty { UUID.nameUUIDFromBytes(legacy.toByteArray(Charsets.UTF_8)).toString() }

internal fun entrySyncId(e: Entry) = stableSyncId(e.syncId, "entry:${e.id}:${e.createdAt}")

internal fun recurringSyncId(r: RecurringBill) =
    stableSyncId(r.syncId, "recurring:${r.id}:${r.name}:${r.currencyCode}")

internal fun installmentSyncId(p: InstallmentPlan) =
    stableSyncId(p.syncId, "installment:${p.id}:${p.name}:${p.currencyCode}")

internal data class RestoreConflict(
    val key: String,
    val title: String,
    val local: String,
    val backup: String,
)

internal data class MergeResult(
    val snapshot: CompleteSnapshot,
    val conflicts: List<RestoreConflict>,
    val addedBills: Int,
    val addedTrash: Int,
    val skippedConfigs: Int,
)

internal data class RestoreComparison(
    val local: CompleteSnapshot,
    val incoming: CompleteSnapshot,
    val preview: MergeResult,
)

private class IdAllocator(ids: List<Long>) {
    private val used = ids.toMutableSet()
    private var next = ids.maxOrNull() ?: 0

    fun take(preferred: Long): Long {
        if (preferred > 0 && used.add(preferred)) return preferred
        do {
            next = Math.incrementExact(next)
        } while (!used.add(next))
        return next
    }
}

private data class BillState(val entry: Entry, val trash: TrashRecord? = null)

/** Pure, linear merge; unresolved conflicts always retain the local version. */
internal fun mergeSnapshots(
    local: CompleteSnapshot,
    incoming: CompleteSnapshot,
    backupChoices: Set<String> = emptySet(),
): MergeResult {
    val conflicts = mutableListOf<RestoreConflict>()
    fun <T, K> combine(
        kind: String,
        left: List<T>,
        right: List<T>,
        key: (T) -> K,
        same: (T, T) -> Boolean = { a, b -> a == b },
        describe: (T) -> String,
    ): List<T> {
        val map = left.associateByTo(linkedMapOf(), key)
        require(map.size == left.size && right.map(key).distinct().size == right.size) {
            "备份包含重复记录标识，无法安全合并"
        }
        right.forEach { item ->
            val id = key(item)
            val previous = map[id]
            if (previous == null) map[id] = item
            else if (!same(previous, item)) {
                val conflictKey = "$kind:$id"
                conflicts += RestoreConflict(conflictKey, kind, describe(previous), describe(item))
                if (conflictKey in backupChoices) map[id] = item
            }
        }
        return map.values.toList()
    }
    val personal = local.personal ?: PersonalBackup()
    val leftBudgets = BudgetRules.decode(personal.budgets)
    val leftTags = TagCatalog.decode(personal.managedTags)
    val categories = local.categories.toMutableList()
    val categoryIds =
        IdAllocator(
            categories.map { it.id } +
                leftBudgets.map { it.categoryId } +
                leftTags.map { it.categoryId }
        )
    val byName = categories.associateByTo(mutableMapOf()) { it.type to it.name }
    val mapping =
        incoming.categories.associate { remote ->
            val name = remote.type to remote.name
            val target =
                byName[name]
                    ?: remote.copy(id = categoryIds.take(remote.id)).also {
                        categories += it
                        byName[name] = it
                    }
            remote.id to target.id
        }
    val categoryNames = categories.associate { it.id to it.name }
    fun states(snapshot: CompleteSnapshot, remote: Boolean): List<BillState> =
        snapshot.entries.map { entry ->
            BillState(
                entry.copy(
                    categoryId =
                        if (remote) mapping.getValue(entry.categoryId) else entry.categoryId,
                    syncId = entrySyncId(entry),
                )
            )
        } +
            snapshot.trash.map { trash ->
                val entry = ArchiveJson.entry(JSONObject(trash.entryJson))
                // A trashed category may already have been removed; its name remains authoritative.
                val target = byName[entry.type to trash.categoryName]?.id ?: 0
                BillState(entry.copy(categoryId = target, syncId = entrySyncId(entry)), trash)
            }
    fun describe(state: BillState): String =
        with(state) {
            val name = trash?.categoryName ?: categoryNames[entry.categoryId].orEmpty()
            "${if (trash == null) "账单" else "回收站"} · ${entry.date()} · $name · ${entry.currencyCode} ${Money.text(entry.amount, entry.currencyCode)}" +
                if (entry.note.isBlank()) "" else "\n${entry.note.take(60)}"
        }
    val leftBills = states(local, false)
    val rightBills = states(incoming, true)
    val bills =
        combine(
            "账单",
            leftBills,
            rightBills,
            { it.entry.syncId },
            same = { a, b ->
                a.entry.copy(id = 0, updatedAt = 0) == b.entry.copy(id = 0, updatedAt = 0) &&
                    (a.trash == null) == (b.trash == null)
            },
            describe = ::describe,
        )
    val activeIds = IdAllocator(local.entries.map { it.id })
    val trashIds = IdAllocator(local.trash.map { it.id })
    val localActive =
        leftBills.filter { it.trash == null }.associate { it.entry.syncId to it.entry.id }
    val localTrash =
        leftBills.filter { it.trash != null }.associate { it.entry.syncId to it.trash!!.id }
    val entries =
        bills
            .filter { it.trash == null }
            .map { it.entry.copy(id = localActive[it.entry.syncId] ?: activeIds.take(it.entry.id)) }
    val trash =
        bills
            .filter { it.trash != null }
            .map {
                it.trash!!.copy(
                    id = localTrash[it.entry.syncId] ?: trashIds.take(it.trash.id),
                    entryJson = ArchiveJson.entry(it.entry).toString(),
                )
            }
    fun <T> ids(
        items: List<T>,
        localIds: Map<String, Long>,
        identity: (T) -> String,
        id: (T) -> Long,
        copy: (T, Long) -> T,
    ): List<T> {
        val allocator = IdAllocator(localIds.values.toList())
        return items.map { copy(it, localIds[identity(it)] ?: allocator.take(id(it))) }
    }
    val leftRules = local.recurring.map { it.copy(syncId = recurringSyncId(it)) }
    val rightRules =
        incoming.recurring.map {
            it.copy(categoryId = mapping.getValue(it.categoryId), syncId = recurringSyncId(it))
        }
    val rules =
        combine(
            "周期账单",
            leftRules,
            rightRules,
            { it.syncId },
            same = { a, b -> a.copy(id = 0) == b.copy(id = 0) },
            describe = {
                "${it.name} · ${it.currencyCode} ${Money.text(it.amount, it.currencyCode)}\n下期 ${it.nextDate} · ${if (it.enabled) "启用" else "暂停"}"
            },
        )
    val recurring =
        ids(rules, leftRules.associate { it.syncId to it.id }, { it.syncId }, { it.id }) { r, id ->
            r.copy(id = id)
        }
    val leftPlans = local.installments.map { it.copy(syncId = installmentSyncId(it)) }
    val rightPlans =
        incoming.installments.map {
            it.copy(categoryId = mapping.getValue(it.categoryId), syncId = installmentSyncId(it))
        }
    val plans =
        combine(
            "分期账单",
            leftPlans,
            rightPlans,
            { it.syncId },
            same = { a, b -> a.copy(id = 0) == b.copy(id = 0) },
            describe = {
                "${it.name} · ${it.currencyCode} ${Money.text(it.principal, it.currencyCode)}\n已入账 ${it.paidTerms}/${it.terms} · 下期 ${it.nextDate}"
            },
        )
    val installments =
        ids(plans, leftPlans.associate { it.syncId to it.id }, { it.syncId }, { it.id }) { p, id ->
            p.copy(id = id)
        }
    val remote = incoming.personal
    var skippedConfigs = 0
    val rightBudgets =
        remote
            ?.let { BudgetRules.decode(it.budgets) }
            .orEmpty()
            .filter {
                (it.categoryId == 0L || it.categoryId in mapping).also { valid ->
                    if (!valid) skippedConfigs++
                }
            }
            .map {
                it.copy(
                    categoryId =
                        if (it.categoryId == 0L) 0
                        else mapping[it.categoryId] ?: error("预算分类不存在，无法合并")
                )
            }
    val budgets =
        combine(
            "预算",
            leftBudgets,
            rightBudgets,
            { it.key },
            describe = {
                "${it.month.ifEmpty { "每月" }} · ${it.currency} · ${if (it.categoryId == 0L) "总预算" else categories.find { c -> c.id == it.categoryId }?.name.orEmpty()} · ${Money.text(it.limit, it.currency)}"
            },
        )
    val rightTags =
        remote
            ?.let { TagCatalog.decode(it.managedTags) }
            .orEmpty()
            .filter { (it.categoryId in mapping).also { valid -> if (!valid) skippedConfigs++ } }
            .map { it.copy(categoryId = mapping[it.categoryId] ?: error("标签分类不存在，无法合并")) }
    val tags =
        combine(
                "标签",
                leftTags,
                rightTags,
                { it.id },
                describe = {
                    "${categories.find { c -> c.id == it.categoryId }?.name.orEmpty()} · ${it.name}"
                },
            )
            .distinctBy { it.categoryId to it.name.lowercase(java.util.Locale.ROOT) }
    require(
        categories.size <= 5000 &&
            entries.size + trash.size <= 100000 &&
            recurring.size <= 1000 &&
            installments.size <= 1000
    ) {
        "合并后数据超过支持上限"
    }
    val localBillKeys = leftBills.mapTo(hashSetOf()) { it.entry.syncId }
    return MergeResult(
        CompleteSnapshot(
            categories,
            entries,
            personal.copy(
                budgets = BudgetRules.encode(budgets),
                managedTags = TagCatalog.encode(tags),
            ),
            trash,
            recurring,
            installments,
        ),
        conflicts,
        rightBills.count { r -> r.trash == null && r.entry.syncId !in localBillKeys },
        rightBills.count { r -> r.trash != null && r.entry.syncId !in localBillKeys },
        skippedConfigs,
    )
}

internal fun sameSnapshot(a: CompleteSnapshot, b: CompleteSnapshot): Boolean =
    a.copy(personal = null) == b.copy(personal = null) &&
        a.personal?.copy(avatar = null) == b.personal?.copy(avatar = null) &&
        (a.personal?.avatar contentEquals b.personal?.avatar)
