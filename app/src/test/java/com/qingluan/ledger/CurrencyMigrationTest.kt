package com.qingluan.ledger

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CurrencyMigrationTest {
    @Test
    fun versionOneDatabaseMigratesWithoutChangingCnyAmounts() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-${System.nanoTime()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        // Match the original exported Room v1 schema, including both transaction indices.
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL(
                "CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, iconKey TEXT NOT NULL, sortOrder INTEGER NOT NULL, isDefault INTEGER NOT NULL)"
            )
            old.execSQL(
                "CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, amount INTEGER NOT NULL, categoryId INTEGER NOT NULL, occurredAt INTEGER NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
            )
            old.execSQL("CREATE INDEX index_transactions_categoryId ON transactions(categoryId)")
            old.execSQL("CREATE INDEX index_transactions_occurredAt ON transactions(occurredAt)")
            old.execSQL("INSERT INTO categories VALUES(1, '餐饮', 'EXPENSE', 'food', 0, 1)")
            old.execSQL(
                "INSERT INTO transactions VALUES(7, 'EXPENSE', 123, 1, 1000, '原账单', 900, 1100)"
            )
            old.version = 1
        }
        val repo = Repository(context, name)
        try {
            assertEquals(
                Entry(7, "EXPENSE", 123, 1, 1000, "原账单", 900, 1100, "CNY"),
                repo.dao.allEntries().single(),
            )
            assertEquals("1.23", Money.text(repo.dao.allEntries().single().amount))
            repo.db.openHelper.writableDatabase.execSQL(
                "INSERT INTO transactions (type, amount, categoryId, occurredAt, note, createdAt, updatedAt) VALUES('EXPENSE', 456, 1, 2000, '', 2000, 2000)"
            )
            assertTrue(repo.dao.allEntries().all { it.currencyCode == "CNY" })
            assertEquals(3, repo.db.openHelper.writableDatabase.version)
        } finally {
            repo.db.close()
            context.deleteDatabase(name)
        }
    }
}
