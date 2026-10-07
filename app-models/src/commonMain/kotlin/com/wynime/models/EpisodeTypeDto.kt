@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import kotlinx.serialization.*

@Serializable
enum class EpisodeTypeDto(val value: kotlin.String) {

    @SerialName(value = "MAIN")
    MAIN("MAIN"),

    @SerialName(value = "SPECIAL")
    SPECIAL("SPECIAL"),

    @SerialName(value = "OP")
    OP("OP"),

    @SerialName(value = "ED")
    ED("ED"),

    @SerialName(value = "TRAILER")
    TRAILER("TRAILER"),

    @SerialName(value = "MAD")
    MAD("MAD"),

    @SerialName(value = "OTHER")
    OTHER("OTHER");

    override fun toString(): kotlin.String = value

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is EpisodeTypeDto) "$data" else null

        fun decode(data: kotlin.Any?): EpisodeTypeDto? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

