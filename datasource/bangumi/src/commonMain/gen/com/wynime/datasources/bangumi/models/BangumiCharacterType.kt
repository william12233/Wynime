@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import kotlinx.serialization.*

@Serializable
enum class BangumiCharacterType(val value: kotlin.Int) {

    @SerialName(value = "1")
    Character(1),

    @SerialName(value = "2")
    Mechanic(2),

    @SerialName(value = "3")
    Ship(3),

    @SerialName(value = "4")
    Organization(4);

    override fun toString(): kotlin.String = value.toString()

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is BangumiCharacterType) "$data" else null

        fun decode(data: kotlin.Any?): BangumiCharacterType? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

