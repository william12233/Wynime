package com.wynime.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("opType")
sealed interface PlaybackHistoryOpDto

