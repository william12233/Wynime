@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import kotlinx.serialization.*

@Serializable
enum class SubjectRelationGraphNodeRoleDto(val value: kotlin.String) {

    @SerialName(value = "MAIN")
    MAIN("MAIN"),

    @SerialName(value = "MAIN_MINOR")
    MAIN_MINOR("MAIN_MINOR"),

    @SerialName(value = "SIDE")
    SIDE("SIDE");

    override fun toString(): kotlin.String = value

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is SubjectRelationGraphNodeRoleDto) "$data" else null

        fun decode(data: kotlin.Any?): SubjectRelationGraphNodeRoleDto? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

