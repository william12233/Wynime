package com.wynime.app.data.models

import kotlinx.serialization.Serializable

@Serializable
data class PaginatedResponse<T>(
    val total: Long,
    val items: List<T>,
)
