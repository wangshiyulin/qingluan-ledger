package com.qingluan.ledger

import android.graphics.Bitmap
import androidx.datastore.preferences.core.*
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompleteBackupTest {
    private lateinit var repo: Repository
    private lateinit var manager: LocalDataManager
    private lateinit var name: String
    private val context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun setup() = runBlocking {
        context.preferences.updateData { emptyPreferences() }
        File(context.filesDir, "restore-journal.json").delete()
        File(context.filesDir, "avatar.jpg").delete()
        name = "complete-${System.nanoTime()}.db"
        repo = Repository(context, name)
        repo.seed()
        manager = LocalDataManager(context, repo)
        repo.save(
            Entry(
                amount = 123,
                categoryId = repo.dao.allCategories().first { it.type == "EXPENSE" }.id,
            )
        )
        context.preferences.edit {
            it[stringPreferencesKey("nickname")] = "备份测试"
            it[stringPreferencesKey("theme")] = "dark"
            it[stringPreferencesKey("palette")] = "blue"
            it[stringPreferencesKey("fontSize")] = "large"
            it[stringPreferencesKey("fontFamily")] = "serif"
            it[booleanPreferencesKey("pureBlack")] = true
            it[booleanPreferencesKey("motion")] = false
        }
        val image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        File(context.filesDir, "avatar.jpg").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }

    @After
    fun close() {
        repo.db.close()
        context.deleteDatabase(name)
        File(context.filesDir, "avatar.jpg").delete()
        File(context.filesDir, "restore-journal.json").delete()
    }

    @Test
    fun fullRoundTripIncludesAvatarAndAllSettings() = runBlocking {
        val raw = manager.export()
        assertEquals(5, JSONObject(raw).getInt("formatVersion"))
        val image = File(context.filesDir, "avatar.jpg").readBytes()
        repo.dao.clear()
        context.preferences.updateData { emptyPreferences() }
        File(context.filesDir, "avatar.jpg").delete()
        manager.restore(raw)
        assertEquals(123L, repo.dao.allEntries().single().amount)
        val prefs = context.preferences.data.first()
        assertEquals("备份测试", prefs[stringPreferencesKey("nickname")])
        assertEquals("dark", prefs[stringPreferencesKey("theme")])
        assertEquals("large", prefs[stringPreferencesKey("fontSize")])
        assertNull(prefs[stringPreferencesKey("fontFamily")])
        assertEquals("blue", prefs[stringPreferencesKey("palette")])
        assertEquals(true, prefs[booleanPreferencesKey("pureBlack")])
        assertEquals(false, prefs[booleanPreferencesKey("motion")])
        assertArrayEquals(image, File(context.filesDir, "avatar.jpg").readBytes())
        assertFalse(File(context.filesDir, "restore-journal.json").exists())
    }

    @Test
    fun legacyBackupsKeepCurrentPersonalization() = runBlocking {
        val raw = repo.backup()
        val image = File(context.filesDir, "avatar.jpg").readBytes()
        repo.dao.clear()
        manager.restore(raw)
        assertEquals(123L, repo.dao.allEntries().single().amount)
        assertEquals("备份测试", context.preferences.data.first()[stringPreferencesKey("nickname")])
        assertArrayEquals(image, File(context.filesDir, "avatar.jpg").readBytes())
        val old = JSONObject(raw).put("formatVersion", 1).put("currency", "CNY")
        val rows = old.getJSONArray("transactions")
        for (i in 0 until rows.length()) rows.getJSONObject(i).remove("currencyCode")
        repo.dao.clear()
        manager.restore(old.toString())
        assertEquals("CNY", repo.dao.allEntries().single().currencyCode)
        val versionThree = JSONObject(manager.export()).put("formatVersion", 3)
        versionThree.getJSONObject("personal").put("fontFamily", "mono").put("glass", false)
        manager.restore(versionThree.toString())
        assertEquals("备份测试", context.preferences.data.first()[stringPreferencesKey("nickname")])
        assertNull(context.preferences.data.first()[stringPreferencesKey("fontFamily")])
        assertNull(context.preferences.data.first()[booleanPreferencesKey("glass")])
    }

    @Test
    fun invalidInputPreservesDataAndInterruptedRestoreRecovers() = runBlocking {
        val raw = manager.export()
        val bad = JSONObject(raw)
        bad.getJSONObject("personal").put("fontSize", "invalid")
        assertTrue(runCatching { manager.restore(bad.toString()) }.isFailure)
        val badAvatar = JSONObject(raw)
        badAvatar.getJSONObject("personal").put("avatar", "!!!!")
        assertTrue(runCatching { manager.restore(badAvatar.toString()) }.isFailure)
        assertEquals(123L, repo.dao.allEntries().single().amount)
        File(context.filesDir, "restore-journal.json").writeText(raw)
        repo.dao.clear()
        File(context.filesDir, "avatar.jpg").delete()
        context.preferences.edit { it[stringPreferencesKey("nickname")] = "中断状态" }
        manager.export() // Access also recovers a journal left by an interrupted operation.
        assertEquals(123L, repo.dao.allEntries().single().amount)
        assertEquals("备份测试", context.preferences.data.first()[stringPreferencesKey("nickname")])
        assertTrue(File(context.filesDir, "avatar.jpg").exists())
        assertFalse(File(context.filesDir, "restore-journal.json").exists())
    }
}
