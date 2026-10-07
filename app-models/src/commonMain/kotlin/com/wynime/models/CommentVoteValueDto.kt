@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import kotlinx.serialization.*

@Serializable
enum class CommentVoteValueDto(val value: kotlin.String) {

    @SerialName(value = "like")
    LIKE("like"),

    @SerialName(value = "dislike")
    DISLIKE("dislike");

    override fun toString(): kotlin.String = value

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is CommentVoteValueDto) "$data" else null

        fun decode(data: kotlin.Any?): CommentVoteValueDto? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

