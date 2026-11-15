package com.qingluan.ledger

import android.database.sqlite.SQLiteDatabase
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Uses the schema actually shipped in the preceding 1.1.3 archive, not the new compiler output. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FrameworkUpgradeTest {
    @Test fun previousVersionSixDatabaseOpensWithoutLosingBills() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "framework-upgrade-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val schema = JSONObject(javaClass.getResourceAsStream("/legacy-schema-6.json")!!.bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path,null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                val indexes = entity.getJSONArray("indices")
                for (j in 0 until indexes.length()) db.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
            }
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table (id,identity_hash) VALUES(42,?)",arrayOf(schema.getString("identityHash")))
            db.execSQL("INSERT INTO categories VALUES(1,'餐饮','EXPENSE','food',0,1)")
            db.execSQL("INSERT INTO categories VALUES(2,'工资','INCOME','salary',0,1)")
            db.execSQL("INSERT INTO transactions (id,type,amount,categoryId,occurredAt,note,createdAt,updatedAt,currencyCode,tags,receipt,syncId) VALUES(9,'EXPENSE',789,1,1000,'升级前账单',900,1100,'CNY','午餐','','')")
            db.version = 6
        }
        val repository = Repository(context,name)
        try {
            val entry = repository.dao.allEntries().single()
            assertEquals(9L,entry.id)
            assertEquals(789L,entry.amount)
            assertEquals("升级前账单",entry.note)
            assertEquals("午餐",entry.tags)
            assertEquals(2,repository.dao.allCategories().size)
        } finally { repository.db.close(); context.deleteDatabase(name) }
    }
}
