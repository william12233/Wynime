package com.wynime.utils.serialization

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

val JsonElement.jsonObjectOrNull get() = (this as? JsonObject)

fun JsonObject.getOrFail(key: String): JsonElement {
    return get(key) ?: throw NoSuchElementException("key $key not found")
}

fun JsonObject.getIntOrFail(key: String): Int {
    val field = get(key)?.jsonPrimitive ?: throw NoSuchElementException("key $key not found")
    return field.intOrNull ?: throw IllegalStateException("Field $key is not an int: $field")
}

fun JsonObject.getString(key: String): String? {
    return get(key)?.jsonPrimitive?.content
}

fun JsonObject.getStringOrFail(key: String): String {
    return getString(key) ?: throw NoSuchElementException("key $key not found")
}

fun JsonObject.getBooleanOrFail(key: String): Boolean {
    val field = get(key)?.jsonPrimitive ?: throw NoSuchElementException("key $key not found")
    return field.booleanOrNull ?: throw IllegalStateException("Field $key is not a boolean: $field")
}
