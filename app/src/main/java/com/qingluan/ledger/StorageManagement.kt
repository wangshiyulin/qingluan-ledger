package com.qingluan.ledger

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.util.Comparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StorageUsage(
    val database: Long,
    val cache: Long,
    val logs: Long,
    val recovery: Long,
    val other: Long,
    val receiptContent: Long,
    val trashContent: Long,
) {
    val total
        get() = database + cache + logs + recovery + other
}

/** Only explicit app-owned disposable locations are eligible for cleanup. */
class StorageManagement(private val context: Context, private val repository: Repository) {
    private fun bytes(root: File): Long {
        if (!Files.exists(root.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) return 0L
        return Files.walk(root.toPath()).use { paths ->
            paths
                .filter { Files.isRegularFile(it, java.nio.file.LinkOption.NOFOLLOW_LINKS) }
                .mapToLong { Files.size(it) }
                .sum()
        }
    }

    private fun atomicBytes(directory: File, name: String): Long =
        listOf(name, "$name.bak", "$name.new").sumOf { bytes(File(directory, it)) }

    suspend fun inspect(): StorageUsage =
        withContext(Dispatchers.IO) {
            val databaseFile =
                context.getDatabasePath(repository.db.openHelper.databaseName ?: "qingluan.db")
            val db =
                listOf(
                        databaseFile,
                        File(databaseFile.path + "-wal"),
                        File(databaseFile.path + "-shm"),
                    )
                    .sumOf(::bytes)
            val logs = atomicBytes(context.filesDir, "runtime-events.json")
            val recovery = atomicBytes(context.noBackupFilesDir, "webdav-before-restore.qlb")
            val other =
                (bytes(context.filesDir) +
                        bytes(context.noBackupFilesDir) +
                        bytes(context.codeCacheDir) - logs - recovery)
                    .coerceAtLeast(0)
            StorageUsage(
                db,
                bytes(context.cacheDir),
                logs,
                recovery,
                other,
                repository.dao.receiptContentBytes(),
                repository.dao.trashContentBytes(),
            )
        }

    suspend fun clearCache() =
        withContext(Dispatchers.IO) {
            val root = context.cacheDir.toPath()
            if (!Files.exists(root)) return@withContext
            var failed = false
            // Files.walk does not follow symbolic links, including links to user data.
            Files.walk(root).use { paths ->
                paths
                    .sorted(Comparator.reverseOrder())
                    .filter { it != root }
                    .forEach {
                        if (runCatching { Files.deleteIfExists(it) }.isFailure) failed = true
                    }
            }
            check(!failed) { "部分缓存未能清理，请稍后重试" }
        }

    /** Call under the same operation mutex as writes/restores, and never inside a transaction. */
    suspend fun compactDatabase() =
        withContext(Dispatchers.IO) {
            check(!repository.db.inTransaction()) { "数据库正在处理操作，请稍后再试" }
            val db = repository.db.openHelper.writableDatabase
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "数据库正在使用，请稍后再试" }
            }
            db.execSQL("VACUUM")
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        }
}
