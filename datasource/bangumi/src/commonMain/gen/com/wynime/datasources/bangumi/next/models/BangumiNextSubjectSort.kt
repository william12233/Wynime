@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import kotlinx.serialization.*

@Serializable
enum class BangumiNextSubjectSort(val value: kotlin.String) {

    @SerialName(value = "rank")
    Rank("rank"),

    @SerialName(value = "trends")
    Trends("trends"),

    @SerialName(value = "collects")
    Collects("collects"),

    @SerialName(value = "date")
    Date("date"),

    @SerialName(value = "title")
    Title("title");

    override fun toString(): kotlin.String = value

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is BangumiNextSubjectSort) "$data" else null

        fun decode(data: kotlin.Any?): BangumiNextSubjectSort? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

