@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import kotlinx.serialization.*

@Serializable
enum class EpisodeCollectionTypeDto(val value: kotlin.String) {

    @SerialName(value = "DONE")
    DONE("DONE");

    override fun toString(): kotlin.String = value

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is EpisodeCollectionTypeDto) "$data" else null

        fun decode(data: kotlin.Any?): EpisodeCollectionTypeDto? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

