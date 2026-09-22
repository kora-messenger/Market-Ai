package com.veltravia.marketscopeai.util

import org.json.JSONObject

/**
 * org.json on Android has a sharp-edged quirk: calling optString(key) on a
 * key whose JSON value is an explicit `null` (not a missing key — an actual
 * JSON null, which is exactly what a nullable backend field like
 * "authorUsername": null serializes to) returns the literal STRING "null"
 * instead of blank. The common `optString(key).ifBlank { null }` pattern
 * does NOT catch this — "null" is 4 non-blank characters — so it silently
 * leaks through and gets rendered as-is (e.g. an "@null" handle).
 *
 * Use this instead of optString(...).ifBlank { null } for any nullable
 * string field coming from the backend.
 */
fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key, "")
    if (value.isBlank() || value == "null") return null
    return value
}
