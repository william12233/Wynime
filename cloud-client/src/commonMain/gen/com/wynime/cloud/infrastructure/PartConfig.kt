package com.wynime.cloud.infrastructure

data class PartConfig<T>(
    val headers: MutableMap<String, String> = mutableMapOf(),
    val body: T? = null
)
