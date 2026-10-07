@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import kotlinx.serialization.*

@Serializable
enum class BangumiNextEpisodeType(val value: kotlin.Int) {

    @SerialName(value = "0")
    Normal(0),

    @SerialName(value = "1")
    Special(1),

    @SerialName(value = "2")
    OP(2),

    @SerialName(value = "3")
    ED(3),

    @SerialName(value = "4")
    Pre(4),

    @SerialName(value = "5")
    MAD(5),

    @SerialName(value = "6")
    Other(6);

    override fun toString(): kotlin.String = value.toString()

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is BangumiNextEpisodeType) "$data" else null

        fun decode(data: kotlin.Any?): BangumiNextEpisodeType? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

