@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import kotlinx.serialization.*

@Serializable
enum class BangumiFullSyncStateDto(val value: kotlin.String) {

    @SerialName(value = "PREPARING")
    PREPARING("PREPARING"),

    @SerialName(value = "FETCHING_SUBJECTS")
    FETCHING_SUBJECTS("FETCHING_SUBJECTS"),

    @SerialName(value = "FETCHING_EPISODES")
    FETCHING_EPISODES("FETCHING_EPISODES"),

    @SerialName(value = "INSERTING_DATABASE")
    INSERTING_DATABASE("INSERTING_DATABASE"),

    @SerialName(value = "FINISHING")
    FINISHING("FINISHING"),

    @SerialName(value = "FINISHED")
    FINISHED("FINISHED");

    override fun toString(): kotlin.String = value

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is BangumiFullSyncStateDto) "$data" else null

        fun decode(data: kotlin.Any?): BangumiFullSyncStateDto? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

