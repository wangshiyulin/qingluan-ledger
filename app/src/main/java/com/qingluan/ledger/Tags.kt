package com.qingluan.ledger

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class ManagedTag(
    val id: String = UUID.randomUUID().toString(),
    val categoryId: Long,
    val name: String,
)

/** Templates are category-scoped; saved bills keep their historical tag names. */
object TagCatalog {
    val key = stringPreferencesKey("categoryTags")
    private val separators = Regex("[,，、;；\\n\\r]+")

    fun names(raw: String): List<String> =
        raw.split(separators).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun toggle(raw: String, name: String): String {
        val current = names(raw)
        return (if (name in current) current - name else current + name).joinToString("、").also {
            require(it.length <= 300) { "所选标签总长度超过300字" }
        }
    }

    fun validate(tag: ManagedTag) {
        require(runCatching { UUID.fromString(tag.id).toString() == tag.id }.getOrDefault(false)) {
            "标签ID无效"
        }
        require(
            tag.categoryId > 0 &&
                tag.name == tag.name.trim() &&
                tag.name.length in 1..24 &&
                tag.name.none { it.isISOControl() || it in ",，、;；" }
        ) {
            "标签名称须为1–24字，不能包含分隔符"
        }
    }

    fun decode(raw: String): List<ManagedTag> {
        require(raw.length <= 150000) { "标签配置过大" }
        val array = JSONArray(raw)
        require(array.length() <= 1000) { "最多创建1000个标签" }
        val list =
            (0 until array.length()).map {
                val j = array.getJSONObject(it)
                ManagedTag(
                        j.getString("id"),
                        ArchiveJson.long(j, "categoryId"),
                        j.getString("name"),
                    )
                    .also(::validate)
            }
        require(
            list.map { it.id }.distinct().size == list.size &&
                list.map { it.categoryId to it.name.lowercase(Locale.ROOT) }.distinct().size ==
                    list.size
        ) {
            "同一分类下的标签不能重复"
        }
        return list
    }

    fun encode(tags: List<ManagedTag>): String =
        JSONArray()
            .apply {
                tags.forEach {
                    put(
                        JSONObject()
                            .put("id", it.id)
                            .put("categoryId", it.categoryId)
                            .put("name", it.name)
                    )
                }
            }
            .toString()
            .also { decode(it) }
}

val LocalTagCatalog = staticCompositionLocalOf<List<ManagedTag>> { emptyList() }
