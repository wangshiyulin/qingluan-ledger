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

    val currentExportIds = MutableStateFlow<Set<Long>>(emptySet())
    val undo = MutableStateFlow<List<Entry>>(emptyList())

    fun deleteEntries(bills: List<Entry>) = action {
        repository.db.withTransaction { bills.forEach { repository.dao.delete(it.id) } }
        if (experience.value.undoDelete) undo.value = bills
        message.value = "已删除 ${bills.size} 笔账单"
    }

    fun undoDelete() = action {
        val bills = undo.value
        repository.db.withTransaction {
            val current = repository.dao.allEntries()
            val cats = repository.dao.allCategories()
            require(bills.all { b -> cats.any { it.id == b.categoryId && it.type == b.type } }) {
                "原分类已不存在"
            }
            (current + bills.filter { b -> current.none { it.id == b.id } })
                .groupBy { it.currencyCode }
                .values
                .forEach { totals(it) }
            bills
                .filter { b -> current.none { it.id == b.id } }
                .forEach { repository.dao.insert(it) }
        }
        undo.value = emptyList()
        message.value = "已撤销删除"
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

    fun avatar(uri: android.net.Uri, done: () -> Unit = {}) = action {
        try {
            storeAvatar(getApplication(), uri)
            getApplication<Application>().preferences.edit {
                it[longPreferencesKey("avatarRevision")] = System.nanoTime()
            }
        } finally {
            done()
        }
    }

    val appearance =
        app.preferences.data
            .map {
                AppAppearance(
                    it[stringPreferencesKey("palette")] ?: "system",
                    it[booleanPreferencesKey("pureBlack")] ?: false,
                    it[stringPreferencesKey("fontSize")] ?: "system",
                )
            }
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

    fun pureBlack(value: Boolean) = action {
        getApplication<Application>().preferences.edit {
            it[booleanPreferencesKey("pureBlack")] = value
        }
    }

    suspend fun restoreData(raw: String) {
        undo.value = emptyList()
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
                it.remove(stringPreferencesKey("fontFamily"))
                it.remove(booleanPreferencesKey("glass"))
            }
            repository.seed()
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
                message.value = e.message ?: "操作失败"
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
