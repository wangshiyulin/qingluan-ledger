package com.qingluan.ledger

import androidx.room.*
import java.time.*
import org.json.*

@Entity(tableName = "trash_entries", indices = [Index("deletedAt")])
data class TrashRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryJson: String,
    val categoryName: String,
    val iconKey: String,
    val deletedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "recurring_bills",
    foreignKeys =
        [
            ForeignKey(
                entity = Category::class,
                parentColumns = ["id"],
                childColumns = ["categoryId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index("categoryId")],
)
data class RecurringBill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String = "EXPENSE",
    val amount: Long,
    val currencyCode: String = "CNY",
    val categoryId: Long,
    val note: String = "",
    val nextDate: String = LocalDate.now().toString(),
    val frequency: String = "MONTH",
    val interval: Int = 1,
    val anchorDay: Int = LocalDate.parse(nextDate).dayOfMonth,
    val anchorMonth: Int = LocalDate.parse(nextDate).monthValue,
    val enabled: Boolean = true,
)

val frequencyNames = linkedMapOf("DAY" to "天", "WEEK" to "周", "MONTH" to "月", "YEAR" to "年")

fun nextRecurringDate(rule: RecurringBill): LocalDate {
    val date = LocalDate.parse(rule.nextDate)
    val next =
        when (rule.frequency) {
            "DAY" -> date.plusDays(rule.interval.toLong())
            "WEEK" -> date.plusWeeks(rule.interval.toLong())
            "MONTH" ->
                YearMonth.from(date).plusMonths(rule.interval.toLong()).let {
                    it.atDay(minOf(rule.anchorDay, it.lengthOfMonth()))
                }
            "YEAR" ->
                YearMonth.of(Math.addExact(date.year, rule.interval), rule.anchorMonth).let {
                    it.atDay(minOf(rule.anchorDay, it.lengthOfMonth()))
                }
            else -> error("周期类型无效")
        }
    require(next.year in 1970..2200) { "下次日期超出支持范围" }
    return next
}

fun validateRecurring(rule: RecurringBill, categories: List<Category>) {
    require(
        rule.id >= 0 && rule.name.isNotBlank() && rule.name.length <= 50 && rule.note.length <= 1000
    ) {
        "周期账单名称或备注无效"
    }
    require(
        rule.type in listOf("INCOME", "EXPENSE") &&
            rule.amount > 0 &&
            Currencies.contains(rule.currencyCode)
    ) {
        "周期账单金额或币种无效"
    }
    require(categories.any { it.id == rule.categoryId && it.type == rule.type }) {
        "周期账单分类不存在或类型不符"
    }
    require(
        rule.frequency in frequencyNames &&
            rule.interval in 1..365 &&
            rule.anchorDay in 1..31 &&
            rule.anchorMonth in 1..12
    ) {
        "周期设置无效"
    }
    require(LocalDate.parse(rule.nextDate).year in 1970..2200) { "周期日期超出支持范围" }
    nextRecurringDate(rule)
}

object ArchiveJson {
    fun long(j: JSONObject, key: String): Long {
        val value = j.get(key)
        require(value is Number) { "数字字段无效" }
        return java.math.BigDecimal(value.toString()).longValueExact()
    }

    fun entry(e: Entry): JSONObject =
        JSONObject()
            .put("id", e.id)
            .put("type", e.type)
            .put("amount", e.amount)
            .put("categoryId", e.categoryId)
            .put("occurredAt", e.occurredAt)
            .put("note", e.note)
            .put("createdAt", e.createdAt)
            .put("updatedAt", e.updatedAt)
            .put("currencyCode", e.currencyCode)
            .put("tags", e.tags)
            .put("receipt", e.receipt)

    fun entry(j: JSONObject): Entry =
        Entry(
                long(j, "id"),
                j.getString("type"),
                long(j, "amount"),
                long(j, "categoryId"),
                long(j, "occurredAt"),
                j.getString("note"),
                long(j, "createdAt"),
                long(j, "updatedAt"),
                j.getString("currencyCode"),
                j.getString("tags"),
                j.getString("receipt"),
            )
            .also {
                require(
                    it.id > 0 &&
                        it.amount > 0 &&
                        it.type in listOf("INCOME", "EXPENSE") &&
                        Currencies.contains(it.currencyCode)
                ) {
                    "回收站账单无效"
                }
                validateBillExtras(it.tags, it.receipt)
            }

    fun trash(t: TrashRecord) =
        JSONObject()
            .put("id", t.id)
            .put("entry", JSONObject(t.entryJson))
            .put("categoryName", t.categoryName)
            .put("iconKey", t.iconKey)
            .put("deletedAt", t.deletedAt)

    fun trash(j: JSONObject) =
        TrashRecord(
                long(j, "id"),
                entry(j.getJSONObject("entry")).let { entry(it).toString() },
                j.getString("categoryName"),
                j.getString("iconKey"),
                long(j, "deletedAt"),
            )
            .also {
                require(
                    it.id > 0 &&
                        it.categoryName.isNotBlank() &&
                        it.iconKey in Repository.icons &&
                        it.deletedAt >= 0
                ) {
                    "回收站记录无效"
                }
            }

    fun recurring(r: RecurringBill) =
        JSONObject()
            .put("id", r.id)
            .put("name", r.name)
            .put("type", r.type)
            .put("amount", r.amount)
            .put("currencyCode", r.currencyCode)
            .put("categoryId", r.categoryId)
            .put("note", r.note)
            .put("nextDate", r.nextDate)
            .put("frequency", r.frequency)
            .put("interval", r.interval)
            .put("anchorDay", r.anchorDay)
            .put("anchorMonth", r.anchorMonth)
            .put("enabled", r.enabled)

    fun recurring(j: JSONObject) =
        RecurringBill(
            long(j, "id"),
            j.getString("name"),
            j.getString("type"),
            long(j, "amount"),
            j.getString("currencyCode"),
            long(j, "categoryId"),
            j.getString("note"),
            j.getString("nextDate"),
            j.getString("frequency"),
            Math.toIntExact(long(j, "interval")),
            Math.toIntExact(long(j, "anchorDay")),
            Math.toIntExact(long(j, "anchorMonth")),
            j.getBoolean("enabled"),
        )
}

suspend fun Repository.recycle(ids: Set<Long>): List<Long> =
    db.withTransaction {
        val categories = dao.allCategories().associateBy { it.id }
        val added = mutableListOf<Long>()
        var count = dao.trashCount()
        val now = System.currentTimeMillis()
        ids.toList().chunked(500).forEach { batch ->
            val entries = dao.entriesByIds(batch)
            require(count + entries.size <= 100000) { "回收站已满，请先清理" }
            added +=
                dao.addTrashList(
                    entries.map { e ->
                        val c = categories[e.categoryId] ?: error("分类不存在")
                        TrashRecord(
                            entryJson = ArchiveJson.entry(e).toString(),
                            categoryName = c.name,
                            iconKey = c.iconKey,
                            deletedAt = now,
                        )
                    }
                )
            dao.deleteIds(entries.map { it.id })
            count += entries.size
        }
        added
    }

suspend fun Repository.restoreTrash(ids: Set<Long>) =
    db.withTransaction {
        val categories = dao.allCategories().toMutableList()
        val activeIds = dao.entryIds().toMutableSet()
        val amounts = mutableMapOf<Pair<String, String>, Long>()
        ids.toList().chunked(500).forEach { batch ->
            val records = dao.trashByIds(batch)
            require(records.size == batch.size) { "回收站记录已变化" }
            records.forEach { record ->
                val e = ArchiveJson.entry(JSONObject(record.entryJson))
                val category =
                    categories.firstOrNull {
                        it.id == e.categoryId && it.type == e.type && it.name == record.categoryName
                    }
                        ?: categories.firstOrNull {
                            it.name == record.categoryName && it.type == e.type
                        }
                val target =
                    category?.id
                        ?: run {
                            require(categories.size < 5000) { "分类数量已达上限" }
                            val created =
                                Category(
                                    name = record.categoryName,
                                    type = e.type,
                                    iconKey = record.iconKey,
                                )
                            val newId = dao.add(created)
                            categories += created.copy(id = newId)
                            newId
                        }
                val key = e.currencyCode to e.type
                val total = amounts.getOrPut(key) { dao.amountTotal(e.currencyCode, e.type, 0) }
                amounts[key] = Math.addExact(total, e.amount)
                val entryId =
                    dao.insert(
                        e.copy(
                            id = if (e.id in activeIds) 0 else e.id,
                            categoryId = target,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                activeIds += entryId
                dao.deleteTrash(record.id)
            }
        }
    }

suspend fun Repository.saveRecurring(rule: RecurringBill) =
    db.withTransaction {
        validateRecurring(rule, dao.allCategories())
        require(rule.id == 0L || dao.recurring(rule.id) != null) { "周期账单已不存在" }
        if (rule.id == 0L) {
            require(dao.allRecurring().size < 1000) { "周期账单数量已达上限" }
            dao.addRecurring(rule)
        } else dao.updateRecurring(rule)
    }

suspend fun Repository.advanceRecurring(id: Long, expectedDate: String, record: Boolean) =
    db.withTransaction {
        val rule = dao.recurring(id) ?: error("周期账单已不存在")
        require(
            rule.enabled &&
                rule.nextDate == expectedDate &&
                !LocalDate.parse(rule.nextDate).isAfter(LocalDate.now())
        ) {
            "周期账单状态已变化，请刷新"
        }
        val next = nextRecurringDate(rule)
        if (record)
            save(
                Entry(
                    type = rule.type,
                    amount = rule.amount,
                    categoryId = rule.categoryId,
                    occurredAt =
                        LocalDate.parse(rule.nextDate)
                            .atTime(LocalTime.now())
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli(),
                    currencyCode = rule.currencyCode,
                    note = rule.note,
                )
            )
        dao.updateRecurring(rule.copy(nextDate = next.toString()))
    }
