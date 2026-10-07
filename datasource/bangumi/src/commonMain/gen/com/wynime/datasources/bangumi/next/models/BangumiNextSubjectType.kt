@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import kotlinx.serialization.*

@Serializable
enum class BangumiNextSubjectType(val value: kotlin.Int) {

    @SerialName(value = "1")
    Book(1),

    @SerialName(value = "2")
    Anime(2),

    @SerialName(value = "3")
    Music(3),

    @SerialName(value = "4")
    Game(4),

    @SerialName(value = "6")
    Real(6);

    override fun toString(): kotlin.String = value.toString()

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is BangumiNextSubjectType) "$data" else null

        fun decode(data: kotlin.Any?): BangumiNextSubjectType? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

