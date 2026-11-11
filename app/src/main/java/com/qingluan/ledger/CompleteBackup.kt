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
    val trashRetentionDays: Int = 30,
    val searchPresets: String = "{}",
    val mainCurrency: String = "CNY",
    val budgets: String = "[]",
    val managedTags: String = "[]",
)

data class CompleteSnapshot(
    val categories: List<Category>,
    val entries: List<Entry>,
    val personal: PersonalBackup?,
    val trash: List<TrashRecord> = emptyList(),
    val recurring: List<RecurringBill> = emptyList(),
)

object CompleteBackup {
    const val MAX_BYTES = 16 * 1024 * 1024

    fun encode(
        categories: List<Category>,
        entries: List<Entry>,
        personal: PersonalBackup,
        trash: List<TrashRecord> = emptyList(),
        recurring: List<RecurringBill> = emptyList(),
    ): String {
        require(entries.size + trash.size <= 100000 && recurring.size <= 1000) { "备份记录数超过限制" }
        require(Currencies.contains(personal.mainCurrency)) { "主币种无效" }
        BudgetRules.decode(personal.budgets)
        TagCatalog.decode(personal.managedTags)
        val a = personal.appearance
        val p =
            JSONObject()
                .put("nickname", personal.nickname)
                .put("trashRetentionDays", personal.trashRetentionDays)
                .put("searchPresets", JSONObject(personal.searchPresets))
                .put("mainCurrency", personal.mainCurrency)
                .put("budgets", org.json.JSONArray(personal.budgets))
                .put("managedTags", org.json.JSONArray(personal.managedTags))
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
                .put("formatVersion", 9)
                .put(
                    "trash",
                    org.json.JSONArray().apply { trash.forEach { put(ArchiveJson.trash(it)) } },
                )
                .put(
                    "recurring",
                    org.json.JSONArray().apply {
                        recurring.forEach { put(ArchiveJson.recurring(it)) }
                    },
                )
                .put("personal", p)
                .toString()
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "完整备份超过16MB限制" }
        return raw
    }

    fun decode(raw: String): CompleteSnapshot {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份超过16MB限制" }
        validateTransferJson(raw)
        val j = JSONObject(raw)
        val version = j.get("formatVersion")
        require(
            version is Number && java.math.BigDecimal(version.toString()).longValueExact() in 1L..9L
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
        val trash =
            if (j.getLong("formatVersion") >= 7) {
                val items = j.getJSONArray("trash")
                require(items.length() + entries.size <= 100000) { "回收站记录超过限制" }
                (0 until items.length()).map { ArchiveJson.trash(items.getJSONObject(it)) }
            } else emptyList()
        val recurring =
            if (j.getLong("formatVersion") >= 7) {
                val items = j.getJSONArray("recurring")
                require(items.length() <= 1000) { "周期规则超过限制" }
                (0 until items.length()).map {
                    ArchiveJson.recurring(items.getJSONObject(it)).also { rule ->
                        require(rule.id > 0)
                        validateRecurring(rule, categories)
                    }
                }
            } else emptyList()
        require(
            trash.map { it.id }.distinct().size == trash.size &&
                recurring.map { it.id }.distinct().size == recurring.size
        ) {
            "备份记录ID重复"
        }
        val retention =
            if (j.getLong("formatVersion") >= 7)
                Math.toIntExact(ArchiveJson.long(p, "trashRetentionDays"))
            else 30
        require(retention in listOf(0, 7, 30, 90)) { "回收站保留期限无效" }
        val savedSearches =
            if (j.getLong("formatVersion") >= 7) p.getJSONObject("searchPresets").toString()
            else "{}"
        require(searchPresets(savedSearches).size == JSONObject(savedSearches).length()) {
            "保存的搜索条件无效"
        }
        val mainCurrency =
            if (j.getLong("formatVersion") >= 8) p.getString("mainCurrency") else "CNY"
        require(Currencies.contains(mainCurrency)) { "主币种无效" }
        val budgets =
            if (j.getLong("formatVersion") >= 8) p.getJSONArray("budgets").toString() else "[]"
        BudgetRules.decode(budgets)
        val managedTags =
            if (j.getLong("formatVersion") >= 9) p.getJSONArray("managedTags").toString() else "[]"
        TagCatalog.decode(managedTags)
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
                retention,
                savedSearches,
                mainCurrency,
                budgets,
                managedTags,
            ),
            trash,
            recurring,
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
                appearanceFromPreferences(prefs),
                UiEffects(
                    prefs[booleanPreferencesKey("motion")] ?: true,
                    prefs[booleanPreferencesKey("haptics")] ?: true,
                ),
                image,
                Experience.decode(JSONObject(prefs[stringPreferencesKey("experience")] ?: "{}")),
                prefs[intPreferencesKey("trashRetentionDays")] ?: 30,
                prefs[stringPreferencesKey("searchPresets")] ?: "{}",
                prefs[BudgetRules.mainCurrencyKey] ?: "CNY",
                prefs[BudgetRules.key] ?: "[]",
                prefs[TagCatalog.key] ?: "[]",
            )
        return repository.db.withTransaction {
            CompleteSnapshot(
                repository.dao.allCategories(),
                repository.dao.allEntries(),
                p,
                repository.dao.allTrash(),
                repository.dao.allRecurring(),
            )
        }
    }

    suspend fun export(): String {
        recover()
        val data = snapshot()
        return withContext(Dispatchers.Default) {
            CompleteBackup.encode(
                data.categories,
                data.entries,
                data.personal!!,
                data.trash,
                data.recurring,
            )
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
                CompleteBackup.encode(
                        old.categories,
                        old.entries,
                        old.personal!!,
                        old.trash,
                        old.recurring,
                    )
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
            repository.dao.clearRecurring()
            repository.dao.clearTrash()
            repository.dao.clear()
            repository.dao.clearCategories()
            data.categories.forEach { repository.dao.add(it) }
            data.entries.forEach { repository.dao.insert(it) }
            data.trash.forEach { repository.dao.addTrash(it) }
            data.recurring.forEach { repository.dao.addRecurring(it) }
            // Legacy bill-only restores replace category IDs but carry no tag-template ownership.
            if (data.personal == null) context.preferences.edit { it.remove(TagCatalog.key) }
            data.personal?.let { p ->
                withContext(Dispatchers.IO) {
                    if (p.avatar == null) remove(avatar) else write(avatar, p.avatar)
                }
                context.preferences.edit { prefs ->
                    prefs[stringPreferencesKey("experience")] = p.experience.json().toString()
                    prefs[intPreferencesKey("trashRetentionDays")] = p.trashRetentionDays
                    prefs[stringPreferencesKey("searchPresets")] = p.searchPresets
                    prefs[BudgetRules.mainCurrencyKey] = p.mainCurrency
                    prefs[BudgetRules.key] = p.budgets
                    prefs[TagCatalog.key] = p.managedTags
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
                CompleteBackup.encode(
                        old.categories,
                        old.entries,
                        old.personal!!,
                        old.trash,
                        old.recurring,
                    )
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
