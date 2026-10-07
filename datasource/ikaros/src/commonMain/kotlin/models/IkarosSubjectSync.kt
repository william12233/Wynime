package com.wynime.datasources.ikaros.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IkarosSubjectSync(
    val subjectId: Long,
    val platform: String?,
    val platformId: String?,
    @SerialName("syncTime") val syncTime: String?,
)