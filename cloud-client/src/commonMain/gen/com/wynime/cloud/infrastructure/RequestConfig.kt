package com.wynime.cloud.infrastructure

data class RequestConfig<T>(
    val method: RequestMethod,
    val path: String,
    val headers: MutableMap<String, String> = mutableMapOf(),
    val params: MutableMap<String, Any> = mutableMapOf(),
    val query: MutableMap<String, List<String>> = mutableMapOf(),
    val requiresAuthentication: Boolean,
    val body: T? = null
)
