package com.wynime.tools.datasourcetestmcp.resolver

import kotlinx.serialization.json.JsonElement

data class ResolveResult(
    val resolvedVideo: ResolvedVideoResult? = null,
    val diagnostics: JsonElement? = null,
    val errors: List<String> = emptyList(),
)
