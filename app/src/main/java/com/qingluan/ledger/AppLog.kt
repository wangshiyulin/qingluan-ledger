package com.qingluan.ledger

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class LogEvent(val time: Long, val action: String, val result: String)

/** Bounded local diagnostics. Never serialize exception messages, bills, URLs or credentials. */
object AppLog {
    private val lock = Any()

    private fun file(context: Context) = AtomicFile(File(context.filesDir, "runtime-events.json"))

    private fun readLocked(context: Context): List<LogEvent> =
        runCatching {
                val raw =
                    file(context).openRead().use { input ->
                        input
                            .readBytes()
                            .also { require(it.size <= 128 * 1024) }
                            .toString(Charsets.UTF_8)
                    }
                val j = JSONArray(raw)
                (maxOf(0,j.length()-100) until j.length()).map { index ->
                    val e = j.getJSONObject(index)
                    LogEvent(e.getLong("time"), e.getString("action"), e.getString("result"))
                }
            }
            .getOrDefault(emptyList())

    suspend fun read(context: Context) =
        withContext(Dispatchers.IO) { synchronized(lock) { readLocked(context) } }

    suspend fun clear(context: Context) =
        withContext(Dispatchers.IO) { synchronized(lock) { file(context).delete() } }

    suspend fun record(context: Context, action: String, error: Throwable? = null) =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                runCatching {
                    val safeAction =
                        action.takeIf { it in setOf("应用启动", "本地操作", "账单保存", "导入", "导出", "WebDAV") } ?: "本地操作"
                    val result =
                        if (error == null) "完成"
                        else
                            "失败 · " +
                                error.javaClass.simpleName
                                    .filter { it.isLetterOrDigit() || it == '_' }
                                    .take(60)
                    val entries =
                        (readLocked(context) +
                                LogEvent(System.currentTimeMillis(), safeAction, result))
                            .takeLast(100)
                    val json =
                        JSONArray().apply {
                            entries.forEach {
                                put(
                                    JSONObject()
                                        .put("time", it.time)
                                        .put("action", it.action)
                                        .put("result", it.result)
                                )
                            }
                        }
                    val atomic = file(context)
                    val stream = atomic.startWrite()
                    try {
                        stream.write(json.toString().toByteArray(Charsets.UTF_8))
                        atomic.finishWrite(stream)
                    } catch (e: Throwable) {
                        atomic.failWrite(stream)
                        throw e
                    }
                }
                Unit
            }
        }

    fun text(events: List<LogEvent>): String {
        val format =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        return events.joinToString("\n") {
            "${format.format(Instant.ofEpochMilli(it.time))} · ${it.action} · ${it.result}"
        }
    }
}
