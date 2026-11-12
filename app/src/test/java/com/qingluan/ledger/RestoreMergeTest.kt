package com.qingluan.ledger

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RestoreMergeTest {
    private val cats =
        listOf(Category(1, "餐饮", "EXPENSE", "food"), Category(2, "工资", "INCOME", "salary"))
    private val bill =
        Entry(
            id = 1,
            amount = 100,
            categoryId = 1,
            createdAt = 1000,
            occurredAt = 2000,
            updatedAt = 2000,
        )

    private fun snapshot(
        entries: List<Entry> = emptyList(),
        categories: List<Category> = cats,
        personal: PersonalBackup = PersonalBackup(),
    ) = CompleteSnapshot(categories, entries, personal)

    @Test
    fun independentSameIdsAreRemappedAndRepeatedMergeDoesNotDuplicate() {
        val local = snapshot(listOf(bill))
        val remoteBill = bill.copy(syncId = java.util.UUID.randomUUID().toString(), amount = 200)
        val remote = snapshot(listOf(remoteBill))
        val once = mergeSnapshots(local, remote)
        assertEquals(2, once.snapshot.entries.size)
        assertEquals(2, once.snapshot.entries.map { it.id }.toSet().size)
        assertEquals(1, once.addedBills)
        assertTrue(once.conflicts.isEmpty())
        assertEquals(once.snapshot, mergeSnapshots(once.snapshot, remote).snapshot)
        val changed = remote.copy(entries = listOf(remoteBill.copy(amount = 300)))
        val conflict = mergeSnapshots(once.snapshot, changed)
        assertEquals(1, conflict.conflicts.size)
        val chosen = mergeSnapshots(once.snapshot, changed, setOf(conflict.conflicts.single().key))
        assertEquals(300L, chosen.snapshot.entries.single { it.syncId == remoteBill.syncId }.amount)
        assertEquals(2, chosen.snapshot.entries.size)
    }

    @Test
    fun conflictsKeepLocalUnlessExplicitlySelectedAndMissingRecordsDoNotDelete() {
        val local = snapshot(listOf(bill))
        val remote = snapshot(listOf(bill.copy(amount = 250, note = "备份修改")))
        val preview = mergeSnapshots(local, remote)
        assertEquals(100L, preview.snapshot.entries.single().amount)
        assertEquals(1, preview.conflicts.size)
        assertEquals(
            250L,
            mergeSnapshots(local, remote, setOf(preview.conflicts.single().key))
                .snapshot
                .entries
                .single()
                .amount,
        )
        assertEquals(1, mergeSnapshots(local, snapshot()).snapshot.entries.size)
    }

    @Test
    fun categoryIdsMapByTypeAndNameAndAllReferencesFollow() {
        val remoteCats =
            listOf(cats[0].copy(id = 9), cats[1].copy(id = 10), Category(1, "交通", "EXPENSE", "car"))
        val local = snapshot()
        val remote =
            snapshot(
                listOf(bill.copy(categoryId = 9)),
                remoteCats,
                PersonalBackup(
                    budgets = BudgetRules.encode(listOf(BudgetRule("CNY", 9, 10000, "2026-11"))),
                    managedTags = TagCatalog.encode(listOf(ManagedTag(categoryId = 9, name = "午餐"))),
                ),
            )
        val result = mergeSnapshots(local, remote).snapshot
        assertEquals(1L, result.entries.single().categoryId)
        assertEquals(1L, BudgetRules.decode(result.personal!!.budgets).single().categoryId)
        assertEquals(1L, TagCatalog.decode(result.personal.managedTags).single().categoryId)
        assertEquals(3, result.categories.size)
        assertEquals(3, result.categories.map { it.id }.toSet().size)
    }

    @Test
    fun activeVersusTrashIsAnExplicitConflict() {
        val local = snapshot(listOf(bill))
        val remote =
            snapshot()
                .copy(
                    trash =
                        listOf(
                            TrashRecord(1, ArchiveJson.entry(bill).toString(), "餐饮", "food", 3000)
                        )
                )
        val preview = mergeSnapshots(local, remote)
        assertEquals(1, preview.conflicts.size)
        assertTrue(preview.snapshot.trash.isEmpty())
        val result = mergeSnapshots(local, remote, setOf(preview.conflicts.single().key)).snapshot
        assertTrue(result.entries.isEmpty())
        assertEquals(
            bill.syncId,
            ArchiveJson.entry(JSONObject(result.trash.single().entryJson)).syncId,
        )
    }

    @Test
    fun monthlyBudgetAndPlanProgressConflictsAreNotSilentlyOverwritten() {
        val rule = RecurringBill(1, "房租", amount = 1000, categoryId = 1, nextDate = "2026-11-01")
        val plan =
            InstallmentPlan(1, "电脑", 1, principal = 10000, terms = 10, nextDate = "2026-11-01")
        val local =
            snapshot(
                    personal =
                        PersonalBackup(
                            nickname = "本机",
                            budgets =
                                BudgetRules.encode(listOf(BudgetRule("CNY", 0, 10000, "2026-11"))),
                        )
                )
                .copy(recurring = listOf(rule), installments = listOf(plan))
        val remote =
            snapshot(
                    personal =
                        PersonalBackup(
                            nickname = "另一设备",
                            budgets =
                                BudgetRules.encode(
                                    listOf(
                                        BudgetRule("CNY", 0, 20000, "2026-11"),
                                        BudgetRule("CNY", 0, 30000, "2026-12"),
                                    )
                                ),
                        )
                )
                .copy(
                    recurring = listOf(rule.copy(nextDate = "2026-12-01")),
                    installments = listOf(plan.copy(paidTerms = 1, nextDate = "2026-12-01")),
                )
        val preview = mergeSnapshots(local, remote)
        assertEquals(3, preview.conflicts.size)
        val result =
            mergeSnapshots(local, remote, preview.conflicts.mapTo(hashSetOf()) { it.key }).snapshot
        assertEquals(1, result.installments.single().paidTerms)
        assertEquals("2026-12-01", result.recurring.single().nextDate)
        assertEquals(2, BudgetRules.decode(result.personal!!.budgets).size)
        assertEquals("本机", result.personal.nickname)
    }

    @Test
    fun legacyBlankIdentityIsDeterministicAndPersistsAfterCollision() {
        val legacy = bill.copy(syncId = "")
        val local = snapshot(listOf(bill.copy(createdAt = 999)))
        val remote = snapshot(listOf(legacy))
        val first = mergeSnapshots(local, remote).snapshot
        assertEquals(2, first.entries.size)
        assertTrue(first.entries.all { it.syncId.isNotBlank() })
        assertEquals(first, mergeSnapshots(first, remote).snapshot)
    }

    @Test
    fun removedCategorySettingsAreReportedAndIdsAreNotReassigned() {
        val local =
            snapshot(
                personal =
                    PersonalBackup(
                        budgets = BudgetRules.encode(listOf(BudgetRule("CNY", 8, 10000)))
                    )
            )
        val remote =
            snapshot(
                categories = cats + Category(8, "新分类", "EXPENSE", "food"),
                personal =
                    PersonalBackup(
                        managedTags =
                            TagCatalog.encode(listOf(ManagedTag(categoryId = 99, name = "失效标签")))
                    ),
            )
        val result = mergeSnapshots(local, remote)
        assertEquals(1, result.skippedConfigs)
        assertNotEquals(8L, result.snapshot.categories.single { it.name == "新分类" }.id)
        assertTrue(TagCatalog.decode(result.snapshot.personal!!.managedTags).isEmpty())
        assertEquals(8L, BudgetRules.decode(result.snapshot.personal.budgets).single().categoryId)
    }

    @Test
    fun duplicateIdentitiesAreRejectedAndAvatarComparisonUsesContent() {
        val duplicate = snapshot(listOf(bill, bill.copy(id = 2)))
        assertTrue(runCatching { mergeSnapshots(snapshot(), duplicate) }.isFailure)
        val a = snapshot(personal = PersonalBackup(avatar = byteArrayOf(1, 2)))
        val b = snapshot(personal = PersonalBackup(avatar = byteArrayOf(1, 2)))
        assertTrue(sameSnapshot(a, b))
        assertFalse(sameSnapshot(a, b.copy(entries = listOf(bill))))
    }
}
