package com.qingluan.ledger

import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransferTest {
    @Test
    fun rangeReplaceKeepsOutsideAndBatchEditIsAtomic() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = Repository(context, "transfer-${System.nanoTime()}.db")
        try {
            repo.seed()
            val category = repo.dao.allCategories().first { it.type == "EXPENSE" }
            val d = LocalDate.of(2026, 11, 10)
            fun at(day: LocalDate) =
                day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            repo.save(
                Entry(amount = 100, categoryId = category.id, occurredAt = at(d.minusDays(1)))
            )
            repo.save(Entry(amount = 200, categoryId = category.id, occurredAt = at(d)))
            val manager = LocalDataManager(context, repo)
            val range = TransferRange(d, d)
            val bill = CsvBill("EXPENSE", 300, category.name, "CNY", at(d), "new", "标签")
            val result = manager.importBills(listOf(bill), range, true, true)
            assertEquals(1, result.added)
            assertEquals(setOf(100L, 300L), repo.dao.allEntries().map { it.amount }.toSet())
            assertEquals(1, manager.importBills(listOf(bill), range, false, true).skipped)
            assertEquals(1, manager.importBills(listOf(bill), range, false, false).added)
            val ids = repo.dao.allEntries().filter { range.contains(it) }.map { it.id }.toSet()
            editBills(repo, ids, null, "统一备注", "旅行")
            assertTrue(
                repo.dao
                    .allEntries()
                    .filter { it.id in ids }
                    .all { it.note == "统一备注" && it.tags == "旅行" }
            )
            val income = repo.dao.allCategories().first { it.type == "INCOME" }
            try {
                editBills(repo, ids, income.id, null, null)
                fail("mixed category should reject")
            } catch (_: IllegalArgumentException) {}
            assertTrue(repo.dao.allEntries().all { it.type == "EXPENSE" })
            val raw =
                CsvExport.encode(
                    repo.dao.allCategories(),
                    repo.dao.allEntries(),
                    includeNote = false,
                    includeTags = true,
                )
            assertEquals(3, CsvImport.decode(raw).size)
            assertTrue(CsvImport.decode(raw).all { it.note == "" })
        } finally {
            repo.db.close()
        }
    }

    @Test
    fun encryptedBackupRoundTripWrongPasswordAndSettings() {
        val cats = listOf(Category(1, "支出", "EXPENSE"), Category(2, "收入", "INCOME"))
        val personal =
            PersonalBackup(experience = Experience(density = "comfortable", statType = "BALANCE"))
        val raw =
            CompleteBackup.encode(
                cats,
                listOf(Entry(id = 1, amount = 100, categoryId = 1, tags = "旅行")),
                personal,
            )
        val encrypted = BackupCipher.encrypt(raw, "long-password".toCharArray())
        val decoded = decodeTransfer(encrypted, "encrypted", "long-password".toCharArray())
        assertEquals("旅行", decoded.bills.single().tags)
        assertEquals(
            personal.experience,
            CompleteBackup.decode(decoded.fullJson!!).personal!!.experience,
        )
        try {
            BackupCipher.decrypt(encrypted, "wrong-password".toCharArray())
            fail("wrong password accepted")
        } catch (_: IllegalArgumentException) {}
    }
}
