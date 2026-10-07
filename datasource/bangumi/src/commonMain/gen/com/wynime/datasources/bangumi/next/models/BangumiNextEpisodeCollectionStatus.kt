@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import kotlinx.serialization.*

@Serializable
enum class BangumiNextEpisodeCollectionStatus(val value: kotlin.Int) {

    @SerialName(value = "0")
    None(0),

    @SerialName(value = "1")
    Wish(1),

    @SerialName(value = "2")
    Done(2),

    @SerialName(value = "3")
    Dropped(3);

    override fun toString(): kotlin.String = value.toString()

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is BangumiNextEpisodeCollectionStatus) "$data" else null

        fun decode(data: kotlin.Any?): BangumiNextEpisodeCollectionStatus? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

