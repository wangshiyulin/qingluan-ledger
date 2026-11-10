package com.qingluan.ledger

import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader

/** Bound nesting before JSONObject builds a tree; reject permissive JSON extensions and duplicates. */
fun validateTransferJson(raw: String) {
    require(raw.toByteArray(Charsets.UTF_8).size <= CompleteBackup.MAX_BYTES) { "备份超过16MB限制" }
    try {
        JsonReader(StringReader(raw)).use { reader ->
            reader.isLenient = false
            fun value(depth: Int) {
                require(depth <= 32) { "JSON嵌套超过限制" }
                when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        reader.beginObject()
                        val keys = mutableSetOf<String>()
                        while (reader.hasNext()) {
                            require(keys.size < 100000) { "JSON字段超过限制" }
                            require(keys.add(reader.nextName())) { "JSON字段重复" }
                            value(depth + 1)
                        }
                        reader.endObject()
                    }
                    JsonToken.BEGIN_ARRAY -> {
                        reader.beginArray()
                        var count = 0
                        while (reader.hasNext()) {
                            require(++count <= 100000) { "备份记录数超过限制" }
                            value(depth + 1)
                        }
                        reader.endArray()
                    }
                    JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                    JsonToken.BOOLEAN -> reader.nextBoolean()
                    JsonToken.NULL -> reader.nextNull()
                    else -> error("JSON格式无效")
                }
            }
            require(reader.peek() == JsonToken.BEGIN_OBJECT) { "JSON格式无效" }
            value(0)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "JSON存在多余内容" }
        }
    } catch (_: Exception) {
        throw IllegalArgumentException("JSON格式无效或超过限制")
    }
}
