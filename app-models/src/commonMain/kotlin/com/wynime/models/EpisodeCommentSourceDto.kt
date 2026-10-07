@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import kotlinx.serialization.*

@Serializable
enum class EpisodeCommentSourceDto(val value: kotlin.String) {

    @SerialName(value = "animeko")
    LEGACY_SERVICE("animeko"),

    @SerialName(value = "bangumi")
    BANGUMI("bangumi");

    override fun toString(): kotlin.String = value

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is EpisodeCommentSourceDto) "$data" else null

        fun decode(data: kotlin.Any?): EpisodeCommentSourceDto? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

