package com.wynime.datasources.api.topic

import kotlinx.serialization.Serializable

@Serializable
data class Author(
    val id: String,
    val name: String,
)
