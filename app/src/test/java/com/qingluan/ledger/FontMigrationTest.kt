package com.qingluan.ledger

import java.time.LocalDateTime
import java.time.ZoneId
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
class FontMigrationTest {
    @Test
    fun customFontBackupLegacyAndMetadataOrder() {
        val categories =
            listOf(
                Category(id = 1, name = "支出", type = "EXPENSE"),
                Category(id = 2, name = "收入", type = "INCOME"),
            )
        val appearance = AppAppearance(fontSize = "custom", customFontPercent = 125)
        assertEquals(1.25f, appearance.scale)
        val raw =
            CompleteBackup.encode(categories, emptyList(), PersonalBackup(appearance = appearance))
        assertEquals(10, JSONObject(raw).getInt("formatVersion"))
        assertEquals(appearance, CompleteBackup.decode(raw).personal!!.appearance)
        val old = JSONObject(raw).put("formatVersion", 5)
        old.getJSONObject("personal").remove("customFontPercent")
        old.getJSONObject("personal").put("fontSize", "large")
        assertEquals(
            115,
            CompleteBackup.decode(old.toString()).personal!!.appearance.customFontPercent,
        )
        val bad = JSONObject(raw)
        bad.getJSONObject("personal").put("customFontPercent", 161)
        assertTrue(runCatching { CompleteBackup.decode(bad.toString()) }.isFailure)
        val at =
            LocalDateTime.of(2026, 11, 10, 12, 34)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val entry = Entry(amount = 100, categoryId = 1, occurredAt = at, note = "备注内容")
        assertEquals("12:34 · 备注内容", entrySubtitle(entry, Experience()))
        assertEquals("备注内容", entrySubtitle(entry, Experience(showTime = false)))
        assertEquals("12:34", entrySubtitle(entry, Experience(showNote = false)))
    }

    @Test
    fun categoryMigrationKeepsCategoriesAndRejectsCrossType() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "category-migration-${System.nanoTime()}.db"
        val repo = Repository(context, name)
        try {
            repo.seed()
            val cats = repo.dao.allCategories()
            val source = cats.first { it.type == "EXPENSE" }
            val target = cats.first { it.type == "EXPENSE" && it.id != source.id }
            val income = cats.first { it.type == "INCOME" }
            repo.save(
                Entry(
                    amount = 123,
                    categoryId = source.id,
                    note = "保留备注",
                    tags = "历史标签",
                    occurredAt = 123456789L,
                )
            )
            val before = repo.dao.allEntries().single()
            assertEquals(1, repo.migrateCategory(source.id, target.id))
            val after = repo.dao.allEntries().single()
            assertEquals(before.copy(categoryId = target.id, updatedAt = after.updatedAt), after)
            assertEquals(cats, repo.dao.allCategories())
            assertTrue(runCatching { repo.migrateCategory(target.id, income.id) }.isFailure)
            assertTrue(runCatching { repo.migrateCategory(target.id, target.id) }.isFailure)
            assertEquals(after, repo.dao.allEntries().single())
            assertEquals(0, repo.dao.references(source.id))
        } finally {
            repo.db.close()
            context.deleteDatabase(name)
        }
    }
}
