package com.wynime.test

actual fun Any.readTestResourceAsString(path: String): String {
    return this::class.java
        .getResource(path)!!
        .readText()
}

actual fun Any.readTestResourceAsByteArray(path: String): ByteArray {
    return this::class.java
        .getResource(path)
        ?.readBytes()
        ?: kotlin.run {
            println("Warning: Resource $path not found")
            byteArrayOf()
        }
}
