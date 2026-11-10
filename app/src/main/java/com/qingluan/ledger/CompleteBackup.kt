package com.qingluan.ledger

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AtomicFile
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.room.withTransaction
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class PersonalBackup(
    val nickname: String = "",
    val theme: String = "system",
    val appearance: AppAppearance = AppAppearance(),
    val effects: UiEffects = UiEffects(),
    val avatar: ByteArray? = null,
    val experience: Experience = Experience(),
)

data class CompleteSnapshot(
    val categories: List<Category>,
    val entries: List<Entry>,
    val personal: PersonalBackup?,
)

object CompleteBackup {
    const val MAX_BYTES = 16 * 1024 * 1024

    fun encode(categories: List<Category>, entries: List<Entry>, personal: PersonalBackup): String {
        val a = personal.appearance
        val p =
            JSONObject()
                .put("nickname", personal.nickname)
                .put("theme", personal.theme)
                .put("palette", a.palette)
                .put("pureBlack", a.pureBlack)
                .put("fontSize", a.fontSize)
                .put("customFontPercent", a.customFontPercent)
                .put("motion", personal.effects.motion)
                .put("haptics", personal.effects.haptics)
                .put("experience", personal.experience.json())
                .put(
                    "avatar",
                    personal.avatar?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
                        ?: JSONObject.NULL,
                )
        val raw =
            JSONObject(Backup.encode(categories, entries))
                .put("formatVersion", 6)
                .put("personal", p)
                .toString()
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "完整备份超过16MB限制" }
        return raw
    }

    fun decode(raw: String): CompleteSnapshot {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份超过16MB限制" }
        val j = JSONObject(raw)
        val version = j.get("formatVersion")
        require(
            version is Number && java.math.BigDecimal(version.toString()).longValueExact() in 1L..6L
        ) {
            "不支持的备份版本"
        }
        if (j.getLong("formatVersion") < 3) {
            val (categories, entries) = Backup.decode(raw)
            return CompleteSnapshot(categories, entries, null)
        }
        val ledger = JSONObject(j.toString()).put("formatVersion", 2)
        val (categories, entries) = Backup.decode(ledger.toString())
        val p = j.getJSONObject("personal")
        val nickname = p.getString("nickname")
        val theme = p.getString("theme")
        val palette = p.getString("palette")
        val fontSize = p.getString("fontSize")
        val customFontPercent =
            if (p.has("customFontPercent")) {
                val value = p.get("customFontPercent")
                require(value is Number) { "自定义字号无效" }
                java.math.BigDecimal(value.toString()).intValueExact().also {
                    require(it in 75..160) { "自定义字号超出范围" }
                }
            } else {
                require(j.getLong("formatVersion") < 6) { "自定义字号字段缺失" }
                100
            }
        require(
            nickname.length <= 24 &&
                theme in listOf("system", "light", "dark") &&
                palette in paletteNames &&
                fontSize in fontSizes
        ) {
            "备份设置无效"
        }
        require(p.has("avatar")) { "头像字段缺失" }
        val avatar =
            if (p.isNull("avatar")) null
            else {
                val text = p.getString("avatar")
                require(text.length <= 1400000) { "头像过大" }
                Base64.decode(text, Base64.NO_WRAP).also { bytes ->
                    require(bytes.size <= 1024 * 1024) { "头像过大" }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    require(bounds.outWidth in 1..512 && bounds.outHeight in 1..512) { "头像无效" }
                    val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("头像无效")
                    image.recycle()
                }
            }
        return CompleteSnapshot(
            categories,
            entries,
            PersonalBackup(
                nickname,
                theme,
                AppAppearance(palette, p.getBoolean("pureBlack"), fontSize, customFontPercent),
                UiEffects(p.getBoolean("motion"), p.getBoolean("haptics")),
                avatar,
                Experience.decode(p.optJSONObject("experience") ?: JSONObject()),
            ),
        )
    }
}

/** Called under the ViewModel operation mutex; journal recovery also runs before seeding. */
class LocalDataManager(private val context: Context, private val repository: Repository) {
    private val journal = AtomicFile(File(context.filesDir, "restore-journal.json"))
    private val avatar = AtomicFile(File(context.filesDir, "avatar.jpg"))

    private fun exists(file: AtomicFile) =
        file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()

    private fun remove(file: AtomicFile) {
        file.delete()
        check(!exists(file)) { "无法清理恢复文件" }
    }

    private fun write(file: AtomicFile, bytes: ByteArray) {
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (e: Throwable) {
            file.failWrite(stream)
            throw e
        }
    }

    private suspend fun snapshot(): CompleteSnapshot {
        val prefs = context.preferences.data.first()
        val image =
            withContext(Dispatchers.IO) {
                if (exists(avatar)) avatar.openRead().use { it.readBytes() } else null
            }
        val p =
            PersonalBackup(
                prefs[stringPreferencesKey("nickname")] ?: "",
                prefs[stringPreferencesKey("theme")] ?: "system",
                AppAppearance(
                    prefs[stringPreferencesKey("palette")] ?: "system",
                    prefs[booleanPreferencesKey("pureBlack")] ?: false,
                    prefs[stringPreferencesKey("fontSize")] ?: "system",
                    (prefs[intPreferencesKey("customFontPercent")] ?: 100).coerceIn(75, 160),
                ),
                UiEffects(
                    prefs[booleanPreferencesKey("motion")] ?: true,
                    prefs[booleanPreferencesKey("haptics")] ?: true,
                ),
                image,
                Experience.decode(JSONObject(prefs[stringPreferencesKey("experience")] ?: "{}")),
            )
        return repository.db.withTransaction {
            CompleteSnapshot(repository.dao.allCategories(), repository.dao.allEntries(), p)
        }
    }

    suspend fun export(): String {
        recover()
        val data = snapshot()
        return withContext(Dispatchers.Default) {
            CompleteBackup.encode(data.categories, data.entries, data.personal!!)
        }
    }

    suspend fun csv(): String {
        recover()
        return repository.db.withTransaction {
            CsvExport.encode(repository.dao.allCategories(), repository.dao.allEntries())
        }
    }

    suspend fun importCsv(raw: String): CsvImportResult {
        val bills = withContext(Dispatchers.Default) { CsvImport.decode(raw) }
        recover()
        return CsvImport.apply(repository, bills)
    }

    suspend fun importBills(
        bills: List<CsvBill>,
        range: TransferRange,
        replace: Boolean,
        skipDuplicates: Boolean,
    ): CsvImportResult {
        recover()
        val old = snapshot()
        withContext(Dispatchers.IO) {
            write(
                journal,
                CompleteBackup.encode(old.categories, old.entries, old.personal!!)
                    .toByteArray(Charsets.UTF_8),
            )
        }
        try {
            val result =
                repository.db.withTransaction {
                    if (replace)
                        repository.dao
                            .allEntries()
                            .filter { range.contains(it) }
                            .forEach { repository.dao.delete(it.id) }
                    CsvImport.apply(repository, bills, skipDuplicates)
                }
            withContext(Dispatchers.IO) { remove(journal) }
            return result
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                try {
                    apply(old)
                    withContext(Dispatchers.IO) { remove(journal) }
                } catch (rollback: Throwable) {
                    e.addSuppressed(rollback)
                }
            }
            throw e
        }
    }

    private suspend fun apply(data: CompleteSnapshot) {
        repository.db.withTransaction {
            repository.dao.clear()
            repository.dao.clearCategories()
            data.categories.forEach { repository.dao.add(it) }
            data.entries.forEach { repository.dao.insert(it) }
            data.personal?.let { p ->
                withContext(Dispatchers.IO) {
                    if (p.avatar == null) remove(avatar) else write(avatar, p.avatar)
                }
                context.preferences.edit { prefs ->
                    prefs[stringPreferencesKey("experience")] = p.experience.json().toString()
                    prefs[stringPreferencesKey("nickname")] = p.nickname
                    prefs[stringPreferencesKey("theme")] = p.theme
                    prefs[stringPreferencesKey("palette")] = p.appearance.palette
                    prefs[booleanPreferencesKey("pureBlack")] = p.appearance.pureBlack
                    prefs[stringPreferencesKey("fontSize")] = p.appearance.fontSize
                    prefs[intPreferencesKey("customFontPercent")] = p.appearance.customFontPercent
                    prefs.remove(stringPreferencesKey("fontFamily"))
                    prefs[booleanPreferencesKey("motion")] = p.effects.motion
                    prefs[booleanPreferencesKey("haptics")] = p.effects.haptics
                    prefs.remove(booleanPreferencesKey("glass"))
                    prefs[longPreferencesKey("avatarRevision")] = System.nanoTime()
                }
            }
        }
    }

    suspend fun recover() =
        withContext(Dispatchers.IO) {
            if (exists(journal)) {
                val old = journal.openRead().use { CompleteBackup.decode(Backup.read(it)) }
                apply(old)
                remove(journal)
            }
        }

    suspend fun restore(raw: String) {
        val next = withContext(Dispatchers.Default) { CompleteBackup.decode(raw) }
        recover()
        val old = snapshot()
        withContext(Dispatchers.IO) {
            write(
                journal,
                CompleteBackup.encode(old.categories, old.entries, old.personal!!)
                    .toByteArray(Charsets.UTF_8),
            )
        }
        try {
            apply(next)
            withContext(Dispatchers.IO) { remove(journal) }
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                try {
                    apply(old)
                    withContext(Dispatchers.IO) { remove(journal) }
                } catch (rollback: Throwable) {
                    failure.addSuppressed(rollback)
                }
            }
            throw failure
        }
    }
}
