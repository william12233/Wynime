@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import kotlinx.serialization.*

@Serializable
enum class CollectionTypeDto(val value: kotlin.String) {

    @SerialName(value = "WISH")
    WISH("WISH"),

    @SerialName(value = "DONE")
    DONE("DONE"),

    @SerialName(value = "DOING")
    DOING("DOING"),

    @SerialName(value = "ON_HOLD")
    ON_HOLD("ON_HOLD"),

    @SerialName(value = "DROPPED")
    DROPPED("DROPPED");

    override fun toString(): kotlin.String = value

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is CollectionTypeDto) "$data" else null

        fun decode(data: kotlin.Any?): CollectionTypeDto? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

