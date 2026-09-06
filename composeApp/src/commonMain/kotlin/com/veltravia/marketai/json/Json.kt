package com.veltravia.marketai.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Multiplatform JSON objects with the same ergonomics as Android's org.json
 * (optString / optDouble / put / length / ...) so all screens share one code base.
 * Backed by kotlinx.serialization.
 */
class JSONObject() {
    private val map = LinkedHashMap<String, Any?>()

    constructor(raw: String) : this() {
        val element = Json.parseToJsonElement(raw)
        require(element is JsonObject) { "Expected JSON object" }
        readInto(element)
    }

    private fun readInto(obj: JsonObject) {
        for ((key, value) in obj) {
            map[key] = when (value) {
                is JsonNull -> null
                is JsonPrimitive -> when {
                    !value.isString && value.content.contains('.') -> value.doubleOrNull
                    !value.isString && (value.content == "true" || value.content == "false") -> value.content == "true"
                    !value.isString -> value.intOrNull ?: value.content
                    else -> value.content
                }
                is JsonObject -> JSONObject().also { it.readInto(value) }
                is JsonArray -> JSONArray(value)
            }
        }
    }

    fun put(key: String, value: Any?): JSONObject {
        map[key] = when (value) {
            null, is String, is Int, is Long, is Double, is Boolean -> value
            is JSONObject, is JSONArray -> value
            else -> value.toString()
        }
        return this
    }

    fun optString(key: String, default: String = ""): String =
        when (val v = map[key]) {
            null -> default
            is String -> v
            is Double -> if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
            is Boolean -> v.toString()
            is Int, is Long -> v.toString()
            else -> v.toString()
        }

    fun optInt(key: String, default: Int = 0): Int = when (val v = map[key]) {
        is Int -> v
        is Long -> v.toInt()
        is Double -> v.toInt()
        is String -> v.toIntOrNull() ?: default
        else -> default
    }

    fun optDouble(key: String, default: Double = Double.NaN): Double = when (val v = map[key]) {
        is Int -> v.toDouble()
        is Long -> v.toDouble()
        is Double -> v
        is String -> v.toDoubleOrNull() ?: default
        else -> default
    }

    fun optBoolean(key: String, default: Boolean = false): Boolean = when (val v = map[key]) {
        is Boolean -> v
        is String -> v == "true"
        else -> default
    }

    fun optJSONObject(key: String): JSONObject? = map[key] as? JSONObject
    fun optJSONArray(key: String): JSONArray? = map[key] as? JSONArray
    fun has(key: String): Boolean = map.containsKey(key)

    fun getString(key: String): String =
        map[key]?.toString() ?: throw NoSuchElementException("No value for $key")

    fun getJSONObject(key: String): JSONObject =
        map[key] as? JSONObject ?: throw NoSuchElementException("No object for $key")

    fun keys(): Set<String> = map.keys

    override fun toString(): String {
        val sb = StringBuilder("{")
        var first = true
        for ((key, value) in map) {
            if (!first) sb.append(',')
            first = false
            quote(key, sb).append(':')
            JSONObject.appendValue(value, sb)
        }
        sb.append('}')
        return sb.toString()
    }

    companion object {
        internal fun quote(s: String, sb: StringBuilder): StringBuilder {
            sb.append('"')
            for (c in s) {
                when (c) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
                }
            }
            return sb.append('"')
        }

        internal fun appendValue(value: Any?, sb: StringBuilder) {
            when (value) {
                null -> sb.append("null")
                is String -> quote(value, sb)
                is Boolean -> sb.append(value)
                is Int, is Long -> sb.append(value)
                is Double -> sb.append(value)
                is JSONObject -> sb.append(value.toString())
                is JSONArray -> sb.append(value.toString())
                else -> quote(value.toString(), sb)
            }
        }
    }
}

class JSONArray {
    private val list = mutableListOf<Any?>()

    constructor()
    constructor(raw: String) : this() {
        val element = Json.parseToJsonElement(raw)
        require(element is JsonArray) { "Expected JSON array" }
        readInto(element)
    }
    internal constructor(element: JsonArray) : this() {
        readInto(element)
    }

    private fun readInto(arr: JsonArray) {
        for (value in arr) {
            list.add(when (value) {
                is JsonNull -> null
                is JsonPrimitive -> when {
                    !value.isString && value.content.contains('.') -> value.doubleOrNull
                    !value.isString && (value.content == "true" || value.content == "false") -> value.content == "true"
                    !value.isString -> value.intOrNull ?: value.content
                    else -> value.content
                }
                is JsonObject -> JSONObject().also { it.readInto(value) }
                is JsonArray -> JSONArray(value)
            })
        }
    }

    fun put(value: Any?): JSONArray {
        list.add(
            when (value) {
                null, is String, is Int, is Long, is Double, is Boolean, is JSONObject, is JSONArray -> value
                else -> value.toString()
            }
        )
        return this
    }

    fun length(): Int = list.size

    fun optString(index: Int, default: String = ""): String =
        list.getOrNull(index)?.toString() ?: default

    fun optInt(index: Int, default: Int = 0): Int = when (val v = list.getOrNull(index)) {
        is Int -> v
        is Long -> v.toInt()
        is Double -> v.toInt()
        is String -> v.toIntOrNull() ?: default
        else -> default
    }

    fun optDouble(index: Int, default: Double = Double.NaN): Double = when (val v = list.getOrNull(index)) {
        is Int -> v.toDouble()
        is Long -> v.toDouble()
        is Double -> v
        is String -> v.toDoubleOrNull() ?: default
        else -> default
    }

    fun optJSONObject(index: Int): JSONObject? = list.getOrNull(index) as? JSONObject
    fun optJSONArray(index: Int): JSONArray? = list.getOrNull(index) as? JSONArray

    override fun toString(): String {
        val sb = StringBuilder("[")
        for (i in 0 until list.size) {
            if (i > 0) sb.append(',')
            JSONObject.appendValue(list[i], sb)
        }
        sb.append(']')
        return sb.toString()
    }
}
