package com.qingluan.ledger

import java.io.File
import java.nio.file.Files
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
class TagStorageTest {
    private val categories = listOf(Category(1, "餐饮", "EXPENSE"), Category(2, "工资", "INCOME"))

    @Test
    fun catalogKeepsCategoryScopeAndRejectsDuplicates() {
        val a = ManagedTag(categoryId = 1, name = "日常")
        val b = ManagedTag(categoryId = 2, name = "日常")
        assertEquals(listOf(a, b), TagCatalog.decode(TagCatalog.encode(listOf(a, b))))
        assertTrue(
            runCatching {
                    TagCatalog.encode(
                        listOf(a, a.copy(id = java.util.UUID.randomUUID().toString()))
                    )
                }
                .isFailure
        )
    }

    @Test
    fun legacyNamesAndSelectionLimitsArePreserved() {
        assertEquals(listOf("旧标签", "午餐"), TagCatalog.names("旧标签,午餐"))
        assertEquals("旧标签", TagCatalog.toggle("旧标签,午餐", "午餐"))
        assertEquals("旧标签、午餐", TagCatalog.toggle("旧标签", "午餐"))
        assertTrue(runCatching { TagCatalog.toggle("甲".repeat(300), "午餐") }.isFailure)
    }

    @Test
    fun malformedCatalogIsRejected() {
        for (tag in
            listOf(
                ManagedTag("invalid", 1, "标签"),
                ManagedTag(categoryId = 0, name = "标签"),
                ManagedTag(categoryId = 1, name = "甲、乙"),
            )) assertTrue(runCatching { TagCatalog.encode(listOf(tag)) }.isFailure)
    }

    @Test
    fun backupIncludesCatalogAndOlderFormatsDefaultToEmpty() {
        val tags = TagCatalog.encode(listOf(ManagedTag(categoryId = 1, name = "午餐")))
        val raw = CompleteBackup.encode(categories, emptyList(), PersonalBackup(managedTags = tags))
        assertEquals(9, JSONObject(raw).getInt("formatVersion"))
        assertEquals(
            TagCatalog.decode(tags),
            TagCatalog.decode(CompleteBackup.decode(raw).personal!!.managedTags),
        )
        val old = JSONObject(raw).put("formatVersion", 8)
        old.getJSONObject("personal").remove("managedTags")
        assertEquals("[]", CompleteBackup.decode(old.toString()).personal!!.managedTags)
    }

    @Test
    fun cacheCleanupKeepsUserFilesAndDoesNotFollowSymlinks() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repo = Repository(context, "storage-test.db")
        try {
            repo.seed()
            val category = repo.dao.allCategories().first { it.type == "EXPENSE" }
            repo.save(Entry(amount = 123, categoryId = category.id))
            val protected =
                File(context.filesDir, "protected-user-data").apply { writeText("keep") }
            val cache = context.cacheDir.apply { mkdirs() }
            File(cache, "disposable").writeText("temporary")
            Files.createSymbolicLink(File(cache, "user-link").toPath(), protected.toPath())
            val manager = StorageManagement(context, repo)
            val usage = manager.inspect()
            assertEquals(
                usage.database + usage.cache + usage.logs + usage.recovery + usage.other,
                usage.total,
            )
            manager.clearCache()
            assertEquals("keep", protected.readText())
            assertTrue(cache.isDirectory)
            assertEquals(0, cache.listFiles()!!.size)
            assertTrue(repo.dao.allCategories().isNotEmpty())
            manager.compactDatabase()
            assertEquals(123L, repo.dao.allEntries().single().amount)
            protected.delete()
            Unit
        } finally {
            repo.db.close()
            context.deleteDatabase("storage-test.db")
        }
    }

    @Test
    fun singlePassTotalsRetainCurrencyAndOverflowChecks() {
        val rows =
            listOf(
                Entry(type = "INCOME", amount = 300, categoryId = 2),
                Entry(amount = 50, categoryId = 1),
            )
        assertEquals(Totals(300, 50), totals(rows))
        assertTrue(runCatching { totals(rows + rows.first().copy(currencyCode = "USD")) }.isFailure)
        assertTrue(
            runCatching { totals(listOf(rows.first().copy(amount = Long.MAX_VALUE), rows.first())) }
                .isFailure
        )
    }
}
