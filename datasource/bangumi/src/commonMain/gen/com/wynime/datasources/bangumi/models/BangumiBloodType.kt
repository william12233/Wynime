@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import kotlinx.serialization.*

@Serializable
enum class BangumiBloodType(val value: kotlin.Int) {

    @SerialName(value = "1")
    A(1),

    @SerialName(value = "2")
    B(2),

    @SerialName(value = "3")
    AB(3),

    @SerialName(value = "4")
    O(4);

    override fun toString(): kotlin.String = value.toString()

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is BangumiBloodType) "$data" else null

        fun decode(data: kotlin.Any?): BangumiBloodType? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

