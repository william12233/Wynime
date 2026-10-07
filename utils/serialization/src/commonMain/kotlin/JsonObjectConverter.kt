package com.wynime.utils.serialization

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is Map<*, *> -> toJsonElement()
    is Collection<*> -> toJsonElement()
    is ByteArray -> this.toList().toJsonElement()
    is CharArray -> this.toList().toJsonElement()
    is ShortArray -> this.toList().toJsonElement()
    is IntArray -> this.toList().toJsonElement()
    is LongArray -> this.toList().toJsonElement()
    is FloatArray -> this.toList().toJsonElement()
    is DoubleArray -> this.toList().toJsonElement()
    is BooleanArray -> this.toList().toJsonElement()
    is Array<*> -> toJsonElement()
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is Enum<*> -> JsonPrimitive(this.toString())
    else -> {
        throw IllegalStateException("Can't serialize unknown type: $this")
    }
}

fun Map<*, *>.toJsonElement(): JsonElement {
    val map = mutableMapOf<String, JsonElement>()
    this.forEach { (key, value) ->
        key as String
        map[key] = value.toJsonElement()
    }
    return JsonObject(map)
}

fun Collection<*>.toJsonElement(): JsonElement {
    return JsonArray(this.map { it.toJsonElement() })
}

fun Array<*>.toJsonElement(): JsonElement {
    return JsonArray(this.map { it.toJsonElement() })
}