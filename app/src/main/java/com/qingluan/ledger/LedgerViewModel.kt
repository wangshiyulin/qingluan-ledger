package com.qingluan.ledger

import android.app.Application
import androidx.datastore.preferences.core.*
import androidx.lifecycle.*
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    val repository = Repository(app)
    val localData = LocalDataManager(app, repository)
    val storage = StorageManagement(app, repository)
    val managedTags =
        app.preferences.data
            .map { it[TagCatalog.key] ?: "[]" }
            .distinctUntilChanged()
            .map { runCatching { TagCatalog.decode(it) }.getOrDefault(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun saveManagedTag(original: ManagedTag?, next: ManagedTag) {
        TagCatalog.validate(next)
        require(repository.dao.allCategories().any { it.id == next.categoryId }) { "所属分类不存在" }
        getApplication<Application>().preferences.edit { prefs ->
            val current = TagCatalog.decode(prefs[TagCatalog.key] ?: "[]")
            require(original == null || current.any { it.id == original.id }) { "标签已变化，请重新打开" }
            prefs[TagCatalog.key] =
                TagCatalog.encode(current.filterNot { it.id == original?.id } + next)
        }
    }

    suspend fun deleteManagedTag(tag: ManagedTag) {
        getApplication<Application>().preferences.edit { prefs ->
            prefs[TagCatalog.key] =
                TagCatalog.encode(
                    TagCatalog.decode(prefs[TagCatalog.key] ?: "[]").filterNot { it.id == tag.id }
                )
        }
    }

    private val operationMutex = Mutex()
    private val initialized = MutableStateFlow(false)
    val entries =
        flow {
                initialized.filter { it }.first()
                emitAll(repository.dao.entries())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories =
        flow {
                initialized.filter { it }.first()
                emitAll(repository.dao.categories())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mainCurrency =
        app.preferences.data
            .map { it[BudgetRules.mainCurrencyKey]?.takeIf(Currencies::contains) ?: "CNY" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "CNY")
    val budgets =
        app.preferences.data
            .map { it[BudgetRules.key] ?: "[]" }
            .distinctUntilChanged()
            .map { runCatching { BudgetRules.decode(it) }.getOrDefault(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun mainCurrency(code: String) = action {
        require(Currencies.contains(code)) { "币种无效" }
        getApplication<Application>().preferences.edit { it[BudgetRules.mainCurrencyKey] = code }
    }

    suspend fun saveBudget(original: BudgetRule?, rule: BudgetRule) {
        require(
            rule.categoryId == 0L ||
                repository.dao.allCategories().any {
                    it.id == rule.categoryId && it.type == "EXPENSE"
                }
        ) {
            "预算分类无效"
        }
        getApplication<Application>().preferences.edit { prefs ->
            val existing = BudgetRules.decode(prefs[BudgetRules.key] ?: "[]")
            require(original == null || existing.any { it.key == original.key }) { "预算已变化，请重新打开" }
            require(existing.none { it.key == rule.key && it.key != original?.key }) {
                "该币种和分类已有预算"
            }
            prefs[BudgetRules.key] =
                BudgetRules.encode(existing.filterNot { it.key == original?.key } + rule)
        }
    }

    suspend fun deleteBudget(rule: BudgetRule) {
        getApplication<Application>().preferences.edit { prefs ->
            prefs[BudgetRules.key] =
                BudgetRules.encode(
                    BudgetRules.decode(prefs[BudgetRules.key] ?: "[]").filterNot {
                        it.key == rule.key
                    }
                )
        }
    }

    val theme =
        app.preferences.data
            .map { it[stringPreferencesKey("theme")] ?: "system" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")
    val effects =
        app.preferences.data
            .map {
                UiEffects(
                    it[booleanPreferencesKey("motion")] ?: true,
                    it[booleanPreferencesKey("haptics")] ?: true,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiEffects())

    val experience =
        app.preferences.data
            .map {
                runCatching {
                        Experience.decode(
                            org.json.JSONObject(it[stringPreferencesKey("experience")] ?: "{}")
                        )
                    }
                    .getOrDefault(Experience())
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, Experience())

    fun experience(key: String, value: Any) = action {
        getApplication<Application>().preferences.edit {
            val j = org.json.JSONObject(it[stringPreferencesKey("experience")] ?: "{}")
            j.put(key, value)
            it[stringPreferencesKey("experience")] = Experience.decode(j).json().toString()
        }
    }

    var pendingExport: String? = null
    var pendingFullBackup = false
    var pendingExportCount = 0

    suspend fun markBackup() {
        getApplication<Application>().preferences.edit {
            it[longPreferencesKey("lastBackup")] = System.currentTimeMillis()
        }
    }

    val today = MutableStateFlow(java.time.LocalDate.now())

    fun refreshDueDate() {
        today.value = java.time.LocalDate.now()
    }

    val multiSelecting = MutableStateFlow(false)
    val currentExportIds = MutableStateFlow<Set<Long>>(emptySet())
    val undo = MutableStateFlow<List<Entry>>(emptyList())

    data class UndoBatch(val token: Long, val ids: Set<Long>)

    val undoBatch = MutableStateFlow<UndoBatch?>(null)
    private var undoSequence = 0L
    val trash =
        flow {
                initialized.filter { it }.first()
                emitAll(repository.dao.trashEntries())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recurring =
        flow {
                initialized.filter { it }.first()
                emitAll(repository.dao.recurringBills())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trashRetentionDays =
        app.preferences.data
            .map { it[intPreferencesKey("trashRetentionDays")] ?: 30 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    fun retention(days: Int) = action {
        require(days in listOf(0, 7, 30, 90))
        getApplication<Application>().preferences.edit {
            it[intPreferencesKey("trashRetentionDays")] = days
        }
        if (days > 0) repository.dao.pruneTrash(System.currentTimeMillis() - days * 86400000L)
    }

    fun deleteEntries(bills: List<Entry>) = action {
        val ids = repository.recycle(bills.map { it.id }.toSet())
        undoBatch.value =
            if (experience.value.undoDelete && ids.isNotEmpty())
                UndoBatch(++undoSequence, ids.toSet())
            else null
        undo.value = if (experience.value.undoDelete && ids.isNotEmpty()) bills else emptyList()
        message.value = "已删除 ${ids.size} 笔账单，已移入回收站"
    }

    fun clearUndo(token: Long? = null) {
        if (token == null || undoBatch.value?.token == token) {
            undoBatch.value = null
            undo.value = emptyList()
        }
    }

    fun undoDelete(batch: UndoBatch? = undoBatch.value) = action {
        require(batch != null && batch.ids.isNotEmpty()) { "没有可撤销的删除" }
        repository.restoreTrash(batch.ids)
        clearUndo(batch.token)
        message.value = "已撤销删除"
    }

    fun restoreArchived(ids: Set<Long>) = action {
        repository.restoreTrash(ids)
        clearUndo()
        message.value = "已恢复 ${ids.size} 笔账单"
    }

    fun permanentlyDelete(ids: Set<Long>) = action {
        repository.db.withTransaction { ids.forEach { repository.dao.deleteTrash(it) } }
        clearUndo()
        message.value = "已彻底删除 ${ids.size} 笔账单"
    }

    fun resetProfile() = action {
        android.util
            .AtomicFile(java.io.File(getApplication<Application>().filesDir, "avatar.jpg"))
            .delete()
        getApplication<Application>().preferences.edit {
            it.remove(stringPreferencesKey("nickname"))
            it[longPreferencesKey("avatarRevision")] = System.nanoTime()
        }
    }

    val profile =
        app.preferences.data
            .map {
                LocalProfile(
                    it[stringPreferencesKey("nickname")] ?: "",
                    it[longPreferencesKey("avatarRevision")] ?: 0L,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalProfile())

    fun nickname(value: String) = action {
        getApplication<Application>().preferences.edit {
            it[stringPreferencesKey("nickname")] = value.trim().take(24)
        }
    }

    val appearance =
        app.preferences.data
            .map(::appearanceFromPreferences)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppAppearance())

    fun preference(key: String, value: String) = action {
        val allowed =
            when (key) {
                "palette" -> paletteNames
                "fontSize" -> fontSizes
                else -> error("无效设置")
            }
        require(value in allowed)
        getApplication<Application>().preferences.edit { it[stringPreferencesKey(key)] = value }
    }

    fun customFontPercent(value: Int, done: () -> Unit = {}) = action {
        require(value in 75..160) { "字号比例需在75%至160%之间" }
        getApplication<Application>().preferences.edit {
            it[stringPreferencesKey("fontSize")] = "custom"
            it[intPreferencesKey("customFontPercent")] = value
        }
        done()
    }

    fun pureBlack(value: Boolean) = action {
        getApplication<Application>().preferences.edit {
            it[booleanPreferencesKey("pureBlack")] = value
        }
    }

    suspend fun restoreData(raw: String) {
        clearUndo()
        localData.restore(raw)
        initialized.value = true
    }

    fun effect(key: String, value: Boolean) = action {
        getApplication<Application>().preferences.edit { it[booleanPreferencesKey(key)] = value }
    }

    val message = MutableStateFlow<String?>(null)
    val saving = MutableStateFlow(false)

    fun save(e: Entry, done: () -> Unit) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            try {
                operationMutex.withLock { repository.save(e) }
                AppLog.record(getApplication(), "账单保存")
                done()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                AppLog.record(getApplication(), "账单保存", ex)
                message.value = ex.message ?: "保存失败"
            } finally {
                saving.value = false
            }
        }
    }

    init {
        action("应用启动") {
            localData.recover()
            getApplication<Application>().preferences.edit {
                val themeKey = stringPreferencesKey("theme")
                if (it[themeKey] !in setOf("system", "light", "dark")) it[themeKey] = "system"
                val experienceKey = stringPreferencesKey("experience")
                it[experienceKey] =
                    runCatching {
                            Experience.decode(org.json.JSONObject(it[experienceKey] ?: "{}"))
                                .json()
                                .toString()
                        }
                        .getOrElse { Experience().json().toString() }
                it.remove(stringPreferencesKey("fontFamily"))
                it.remove(booleanPreferencesKey("glass"))
            }
            repository.seed()
            val keep =
                getApplication<Application>()
                    .preferences
                    .data
                    .first()[intPreferencesKey("trashRetentionDays")] ?: 30
            if (keep > 0) repository.dao.pruneTrash(System.currentTimeMillis() - keep * 86400000L)
            initialized.value = true
        }
    }

    fun action(label: String = "本地操作", record: Boolean = true, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                operationMutex.withLock { block() }
                if (record) AppLog.record(getApplication(), label)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (record) AppLog.record(getApplication(), label, e)
                message.value =
                    if (label == "WebDAV") "WebDAV 操作失败，请查看同步页提示" else e.message ?: "操作失败"
            }
        }
    }

    override fun onCleared() {
        CoroutineScope(Dispatchers.IO).launch { operationMutex.withLock { repository.db.close() } }
        super.onCleared()
    }

    fun theme(value: String) = action {
        getApplication<Application>().preferences.edit { it[stringPreferencesKey("theme")] = value }
    }
}
