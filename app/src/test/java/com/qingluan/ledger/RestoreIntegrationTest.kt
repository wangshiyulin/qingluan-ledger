package com.qingluan.ledger

import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestoreIntegrationTest {
    @Test
    fun mergeIsJournaledStableAndRejectsStalePreviews() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.preferences.updateData { emptyPreferences() }
        val name = "restore-merge-${System.nanoTime()}.db"
        val repo = Repository(context, name)
        val manager = LocalDataManager(context, repo)
        try {
            repo.seed()
            val cats = repo.dao.allCategories()
            val local = Entry(amount = 100, categoryId = cats.first { it.type == "EXPENSE" }.id)
            repo.save(local)
            val stored = repo.dao.allEntries().single()
            val incoming = stored.copy(amount = 200, note = "远端修改")
            val raw = CompleteBackup.encode(cats, listOf(incoming), PersonalBackup())
            val preview = manager.compareRestore(raw)
            assertEquals(1, preview.preview.conflicts.size)
            manager.merge(preview, setOf(preview.preview.conflicts.single().key))
            assertEquals(200L, repo.dao.allEntries().single().amount)
            assertFalse(File(context.filesDir, "restore-journal.json").exists())
            val exported = manager.export()
            assertEquals(11, JSONObject(exported).getInt("formatVersion"))
            assertEquals(stored.syncId, CompleteBackup.decode(exported).entries.single().syncId)
            assertTrue(manager.compareRestore(raw).preview.conflicts.isEmpty())
            val stale = manager.compareRestore(raw)
            repo.save(repo.dao.allEntries().single().copy(amount = 300))
            assertTrue(runCatching { manager.merge(stale, emptySet()) }.isFailure)
            assertEquals(300L, repo.dao.allEntries().single().amount)
            assertFalse(File(context.filesDir, "restore-journal.json").exists())
            // Choosing an older plan progress during merge must not create an already paid term
            // twice.
            val plan =
                InstallmentPlan(
                    name = "电脑",
                    categoryId = stored.categoryId,
                    principal = 1000,
                    terms = 2,
                    nextDate = java.time.LocalDate.now().toString(),
                )
            repo.saveInstallment(plan)
            val savedPlan = repo.dao.allInstallments().single()
            val term =
                Entry(
                    amount = 500,
                    categoryId = stored.categoryId,
                    syncId = stableSyncId("", "installment-entry:${installmentSyncId(savedPlan)}:0"),
                )
            repo.save(term)
            repo.confirmInstallment(savedPlan.id, 0)
            assertEquals(2, repo.dao.allEntries().size)
            assertEquals(1, repo.dao.allInstallments().single().paidTerms)
        } finally {
            repo.db.close()
            context.deleteDatabase(name)
            File(context.filesDir, "restore-journal.json").delete()
        }
    }
}
