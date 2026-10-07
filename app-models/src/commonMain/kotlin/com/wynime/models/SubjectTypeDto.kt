@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import kotlinx.serialization.*

@Serializable
enum class SubjectTypeDto(val value: kotlin.String) {

    @SerialName(value = "ANIME")
    ANIME("ANIME");

    override fun toString(): kotlin.String = value

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is SubjectTypeDto) "$data" else null

        fun decode(data: kotlin.Any?): SubjectTypeDto? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

