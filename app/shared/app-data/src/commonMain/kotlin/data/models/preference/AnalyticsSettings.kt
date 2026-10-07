package com.wynime.app.data.models.preference

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class AnalyticsSettings(
    val isBugReportEnabled: Boolean = true,
    val isAnalyticsEnabled: Boolean = true,

    val deviceId: String = Uuid.random().toString(),
) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun default() = AnalyticsSettings()

    }
}
