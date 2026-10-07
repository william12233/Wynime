@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import kotlinx.serialization.*

@Serializable
enum class BangumiNextCollectionType(val value: kotlin.Int) {

    @SerialName(value = "1")
    Wish(1),

    @SerialName(value = "2")
    Collect(2),

    @SerialName(value = "3")
    Doing(3),

    @SerialName(value = "4")
    OnHold(4),

    @SerialName(value = "5")
    Dropped(5);

    override fun toString(): kotlin.String = value.toString()

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is BangumiNextCollectionType) "$data" else null

        fun decode(data: kotlin.Any?): BangumiNextCollectionType? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

