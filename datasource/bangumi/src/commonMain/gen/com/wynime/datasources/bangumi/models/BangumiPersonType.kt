@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import kotlinx.serialization.*

@Serializable
enum class BangumiPersonType(val value: kotlin.Int) {

    @SerialName(value = "1")
    Individual(1),

    @SerialName(value = "2")
    Corporation(2),

    @SerialName(value = "3")
    Association(3);

    override fun toString(): kotlin.String = value.toString()

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is BangumiPersonType) "$data" else null

        fun decode(data: kotlin.Any?): BangumiPersonType? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

