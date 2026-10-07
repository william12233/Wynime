@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import kotlinx.serialization.*

@Serializable
enum class BangumiPersonCareer(val value: kotlin.String) {

    @SerialName(value = "producer")
    PRODUCER("producer"),

    @SerialName(value = "mangaka")
    MANGAKA("mangaka"),

    @SerialName(value = "artist")
    ARTIST("artist"),

    @SerialName(value = "seiyu")
    SEIYU("seiyu"),

    @SerialName(value = "writer")
    WRITER("writer"),

    @SerialName(value = "illustrator")
    ILLUSTRATOR("illustrator"),

    @SerialName(value = "actor")
    ACTOR("actor");

    override fun toString(): kotlin.String = value

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is BangumiPersonCareer) "$data" else null

        fun decode(data: kotlin.Any?): BangumiPersonCareer? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

