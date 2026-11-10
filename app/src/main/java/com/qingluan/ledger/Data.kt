package com.qingluan.ledger

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.json.*

val Context.preferences by preferencesDataStore("appearance")

@Dao
interface LedgerDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    fun entries(): Flow<List<Entry>>

    @Query("SELECT * FROM transactions WHERE id=:id") suspend fun entry(id: Long): Entry?

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun entriesByIds(ids: List<Long>): List<Entry>

    @Query("SELECT id FROM transactions") suspend fun entryIds(): List<Long>

    @Query("DELETE FROM transactions WHERE id IN (:ids)") suspend fun deleteIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM trash_entries") suspend fun trashCount(): Int

    @Query("SELECT * FROM trash_entries WHERE id IN (:ids)")
    suspend fun trashByIds(ids: List<Long>): List<TrashRecord>

    @Insert suspend fun addTrashList(records: List<TrashRecord>): List<Long>

    @Query("SELECT * FROM trash_entries ORDER BY deletedAt DESC, id DESC")
    fun trashEntries(): Flow<List<TrashRecord>>

    @Query("SELECT * FROM trash_entries ORDER BY deletedAt DESC, id DESC")
    suspend fun allTrash(): List<TrashRecord>

    @Query("SELECT * FROM trash_entries WHERE id=:id") suspend fun trash(id: Long): TrashRecord?

    @Insert suspend fun addTrash(record: TrashRecord): Long

    @Query("DELETE FROM trash_entries WHERE id=:id") suspend fun deleteTrash(id: Long)

    @Query("DELETE FROM trash_entries") suspend fun clearTrash()

    @Query("DELETE FROM trash_entries WHERE deletedAt < :cutoff")
    suspend fun pruneTrash(cutoff: Long)

    @Query("SELECT * FROM recurring_bills ORDER BY nextDate, id")
    fun recurringBills(): Flow<List<RecurringBill>>

    @Query("SELECT * FROM recurring_bills ORDER BY nextDate, id")
    suspend fun allRecurring(): List<RecurringBill>

    @Query("SELECT * FROM recurring_bills WHERE id=:id")
    suspend fun recurring(id: Long): RecurringBill?

    @Insert suspend fun addRecurring(rule: RecurringBill): Long

    @Update suspend fun updateRecurring(rule: RecurringBill)

    @Query("DELETE FROM recurring_bills WHERE id=:id") suspend fun deleteRecurring(id: Long)

    @Query("DELETE FROM recurring_bills") suspend fun clearRecurring()

    @Query("SELECT COUNT(*) FROM recurring_bills WHERE categoryId=:id")
    suspend fun recurringReferences(id: Long): Int

    @Query("UPDATE recurring_bills SET categoryId=:target WHERE categoryId=:source")
    suspend fun migrateRecurring(source: Long, target: Long)

    @Query("SELECT * FROM categories ORDER BY sortOrder,id") fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM transactions") suspend fun allEntries(): List<Entry>

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE id=:id)")
    suspend fun entryExists(id: Long): Boolean

    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE id != :excludingId AND currencyCode=:currency AND type=:type"
    )
    suspend fun amountTotal(currency: String, type: String, excludingId: Long): Long

    @Query("SELECT * FROM categories ORDER BY sortOrder,id")
    suspend fun allCategories(): List<Category>

    @Insert suspend fun insert(e: Entry): Long

    @Update suspend fun update(e: Entry)

    @Query("DELETE FROM transactions WHERE id=:id") suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun add(c: Category): Long

    @Update suspend fun updateCategory(c: Category)

    @Query("UPDATE transactions SET categoryId=:target, updatedAt=:now WHERE categoryId=:source")
    suspend fun migrate(source: Long, target: Long, now: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId=:id")
    suspend fun references(id: Long): Int

    @Query("DELETE FROM categories WHERE id=:id") suspend fun deleteCategory(id: Long)

    @Query("DELETE FROM transactions") suspend fun clear()

    @Query("DELETE FROM categories") suspend fun clearCategories()
}

@Database(
    entities = [Entry::class, Category::class, TrashRecord::class, RecurringBill::class],
    version = 4,
    exportSchema = true,
)
abstract class LedgerDb : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS trash_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, entryJson TEXT NOT NULL, categoryName TEXT NOT NULL, iconKey TEXT NOT NULL, deletedAt INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_trash_entries_deletedAt ON trash_entries(deletedAt)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS recurring_bills (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, amount INTEGER NOT NULL, currencyCode TEXT NOT NULL, categoryId INTEGER NOT NULL, note TEXT NOT NULL, nextDate TEXT NOT NULL, frequency TEXT NOT NULL, interval INTEGER NOT NULL, anchorDay INTEGER NOT NULL, anchorMonth INTEGER NOT NULL, enabled INTEGER NOT NULL, FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_recurring_bills_categoryId ON recurring_bills(categoryId)"
                    )
                }
            }
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                    db.execSQL(
                        "ALTER TABLE transactions ADD COLUMN receipt TEXT NOT NULL DEFAULT ''"
                    )
                }
            }
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE transactions ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'CNY'"
                    )
                }
            }
    }
}

class Repository(context: Context, databaseName: String = "qingluan.db") {
    val db =
        Room.databaseBuilder(context, LedgerDb::class.java, databaseName)
            .addMigrations(LedgerDb.MIGRATION_1_2, LedgerDb.MIGRATION_2_3, LedgerDb.MIGRATION_3_4)
            .build()
    val dao = db.dao()

    suspend fun seed() =
        db.withTransaction {
            if (dao.allCategories().isEmpty()) {
                listOf("餐饮", "购物", "交通", "居住", "日用", "医疗", "娱乐", "旅行", "教育", "人情", "其他")
                    .forEachIndexed { i, n ->
                        dao.add(
                            Category(
                                name = n,
                                type = "EXPENSE",
                                sortOrder = i,
                                isDefault = true,
                                iconKey = icons[i % icons.size],
                            )
                        )
                    }
                val incomeIcons = listOf("salary", "gift", "stock", "work", "refund", "money")
                listOf("工资", "奖金", "理财", "兼职", "退款", "其他").forEachIndexed { i, n ->
                    dao.add(
                        Category(
                            name = n,
                            type = "INCOME",
                            sortOrder = i,
                            isDefault = true,
                            iconKey = incomeIcons[i],
                        )
                    )
                }
            }
        }

    suspend fun save(e: Entry) =
        db.withTransaction {
            validateBillExtras(e.tags, e.receipt)
            require(e.amount > 0)
            require(Currencies.contains(e.currencyCode)) { "不支持的币种" }
            require(dao.allCategories().any { it.id == e.categoryId && it.type == e.type })
            require(e.id == 0L || dao.entryExists(e.id)) { "账单已不存在" }
            for (type in listOf("INCOME", "EXPENSE")) {
                Math.addExact(
                    dao.amountTotal(e.currencyCode, type, e.id),
                    if (e.type == type) e.amount else 0L,
                )
            }
            if (e.id == 0L) dao.insert(e) else dao.update(e)
        }

    suspend fun category(c: Category) =
        db.withTransaction {
            require(c.name.trim().isNotEmpty()) { "分类名称不能为空" }
            require(c.type in listOf("INCOME", "EXPENSE") && c.iconKey in icons) { "分类类型或图标无效" }
            if (c.id != 0L) {
                val existing = dao.allCategories().find { it.id == c.id }
                require(existing != null && existing.type == c.type) { "不能改变已有分类的收支类型" }
            }
            require(
                dao.allCategories().none {
                    it.id != c.id && it.type == c.type && it.name == c.name.trim()
                }
            )
            if (c.id == 0L) dao.add(c.copy(name = c.name.trim()))
            else dao.updateCategory(c.copy(name = c.name.trim()))
        }

    suspend fun remove(c: Category, target: Long?) =
        db.withTransaction {
            val same = dao.allCategories().filter { it.type == c.type && it.id != c.id }
            require(same.isNotEmpty()) { "每种类型至少保留一个分类" }
            if (dao.references(c.id) > 0 || dao.recurringReferences(c.id) > 0) {
                require(same.any { it.id == target }) { "请先选择历史账单迁移分类" }
                dao.migrate(c.id, target!!, System.currentTimeMillis())
                dao.migrateRecurring(c.id, target)
            }
            dao.deleteCategory(c.id)
        }

    suspend fun migrateCategory(sourceId: Long, targetId: Long): Int =
        db.withTransaction {
            require(sourceId != targetId) { "来源与目标分类不能相同" }
            val categories = dao.allCategories().associateBy { it.id }
            val source = categories[sourceId] ?: error("来源分类已不存在")
            val target = categories[targetId] ?: error("目标分类已不存在")
            require(source.type == target.type) { "只能迁移到相同收支类型的分类" }
            val count = dao.references(sourceId)
            dao.migrate(sourceId, targetId, System.currentTimeMillis())
            count
        }

    suspend fun reorderCategories(type: String, ids: List<Long>) =
        db.withTransaction {
            val current = dao.allCategories().filter { it.type == type }
            require(
                ids.size == current.size &&
                    ids.toSet().size == ids.size &&
                    ids.toSet() == current.map { it.id }.toSet()
            ) {
                "分类顺序已变化"
            }
            val lookup = current.associateBy { it.id }
            ids.forEachIndexed { order, id ->
                dao.updateCategory(lookup.getValue(id).copy(sortOrder = order))
            }
        }

    suspend fun backup(): String =
        db.withTransaction { Backup.encode(dao.allCategories(), dao.allEntries()) }

    suspend fun restore(raw: String) {
        val (cs, es) = withContext(Dispatchers.Default) { Backup.decode(raw) }
        db.withTransaction {
            dao.clearRecurring()
            dao.clearTrash()
            dao.clear()
            dao.clearCategories()
            cs.forEach { dao.add(it) }
            es.forEach { dao.insert(it) }
        }
    }

    companion object {
        val icons = CategoryIcons.labels.keys.toList()
    }
}

object Backup {
    fun read(input: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(out.size() + n <= 16 * 1024 * 1024) { "备份超过16MB限制" }
            out.write(buffer, 0, n)
        }
        return out.toString("UTF-8")
    }

    private fun number(o: JSONObject, k: String): Long {
        val v = o.get(k)
        require(v is Number)
        return java.math.BigDecimal(v.toString()).longValueExact()
    }

    fun encode(cs: List<Category>, es: List<Entry>): String =
        JSONObject()
            .put("formatVersion", 2)
            .put("currency", "MULTI")
            .put(
                "categories",
                JSONArray().apply {
                    cs.forEach {
                        put(
                            JSONObject()
                                .put("id", it.id)
                                .put("name", it.name)
                                .put("type", it.type)
                                .put("iconKey", it.iconKey)
                                .put("sortOrder", it.sortOrder)
                                .put("isDefault", it.isDefault)
                        )
                    }
                },
            )
            .put(
                "transactions",
                JSONArray().apply {
                    es.forEach {
                        put(
                            JSONObject()
                                .put("id", it.id)
                                .put("type", it.type)
                                .put("amount", it.amount)
                                .put("categoryId", it.categoryId)
                                .put("occurredAt", it.occurredAt)
                                .put("note", it.note)
                                .put("createdAt", it.createdAt)
                                .put("updatedAt", it.updatedAt)
                                .put("currencyCode", it.currencyCode)
                                .put("tags", it.tags)
                                .put("receipt", it.receipt)
                        )
                    }
                },
            )
            .toString(2)

    fun decode(raw: String): Pair<List<Category>, List<Entry>> {
        val j = JSONObject(raw)
        val version = number(j, "formatVersion")
        require(
            (version == 1L && j.getString("currency") == "CNY") ||
                (version == 2L && j.getString("currency") == "MULTI")
        ) {
            "不支持的备份版本或币种"
        }
        val ca = j.getJSONArray("categories")
        val ea = j.getJSONArray("transactions")
        require(ca.length() <= 5000 && ea.length() <= 100000) { "备份记录数超过限制" }
        val cs =
            (0 until ca.length()).map {
                val c = ca.getJSONObject(it)
                Category(
                    number(c, "id"),
                    c.getString("name"),
                    c.getString("type"),
                    c.getString("iconKey"),
                    Math.toIntExact(number(c, "sortOrder")),
                    c.getBoolean("isDefault"),
                )
            }
        val es =
            (0 until ea.length()).map {
                val e = ea.getJSONObject(it)
                Entry(
                    number(e, "id"),
                    e.getString("type"),
                    number(e, "amount"),
                    number(e, "categoryId"),
                    number(e, "occurredAt"),
                    e.getString("note"),
                    number(e, "createdAt"),
                    number(e, "updatedAt"),
                    if (version == 1L) "CNY" else e.getString("currencyCode"),
                    e.optString("tags", ""),
                    e.optString("receipt", ""),
                )
            }
        require(
            cs.map { it.id }.distinct().size == cs.size &&
                es.map { it.id }.distinct().size == es.size
        )
        require(listOf("INCOME", "EXPENSE").all { t -> cs.any { it.type == t } })
        require(
            cs.all {
                it.id > 0 &&
                    it.name.isNotBlank() &&
                    it.type in listOf("INCOME", "EXPENSE") &&
                    it.iconKey in Repository.icons
            }
        )
        val categoriesById = cs.associateBy { it.id }
        require(
            es.all { e ->
                e.id > 0 &&
                    e.amount > 0 &&
                    Currencies.contains(e.currencyCode) &&
                    categoriesById[e.categoryId]?.type == e.type
            }
        )
        es.forEach { validateBillExtras(it.tags, it.receipt) }
        es.groupBy { it.currencyCode }.values.forEach { totals(it) }
        return cs to es
    }
}
