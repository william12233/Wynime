package com.wynime.utils.jsonpath

import com.nfeld.jsonpathkt.kotlinx.resolveAsStringOrNull
import com.nfeld.jsonpathkt.kotlinx.resolveOrNull
import kotlinx.serialization.json.JsonElement
import org.intellij.lang.annotations.Language

typealias JsonPath = com.nfeld.jsonpathkt.JsonPath

fun com.nfeld.jsonpathkt.JsonPath.Companion.compileOrNull(
    @Language("jsonpath") expression: String
): JsonPath? = try {
    compile(expression)
} catch (e: Exception) {
    null
}

fun JsonElement.resolveOrNull(path: JsonPath): JsonElement? = this.resolveOrNull(path)
fun JsonElement.resolveAsStringOrNull(path: JsonPath): String? = this.resolveAsStringOrNull(path)
