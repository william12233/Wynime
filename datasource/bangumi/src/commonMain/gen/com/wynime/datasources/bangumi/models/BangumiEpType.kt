@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import kotlinx.serialization.*

@Serializable
enum class BangumiEpType(val value: kotlin.Int) {

    @SerialName(value = "0")
    MainStory(0),

    @SerialName(value = "1")
    SP(1),

    @SerialName(value = "2")
    OP(2),

    @SerialName(value = "3")
    ED(3),

    @SerialName(value = "4")
    PV(4),

    @SerialName(value = "5")
    MAD(5),

    @SerialName(value = "6")
    Other(6);

    override fun toString(): kotlin.String = value.toString()

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is BangumiEpType) "$data" else null

        fun decode(data: kotlin.Any?): BangumiEpType? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

