@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import kotlinx.serialization.*

@Serializable
enum class BangumiUserGroup(val value: kotlin.Int) {

    @SerialName(value = "1")
    Admin(1),

    @SerialName(value = "2")
    BangumiAdmin(2),

    @SerialName(value = "3")
    DoujinAdmin(3),

    @SerialName(value = "4")
    MutedUser(4),

    @SerialName(value = "5")
    BlockedUser(5),

    @SerialName(value = "8")
    PersonAdmin(8),

    @SerialName(value = "9")
    WikiAdmin(9),

    @SerialName(value = "10")
    User(10),

    @SerialName(value = "11")
    WikiUser(11);

    override fun toString(): kotlin.String = value.toString()

    companion object {

        fun encode(data: kotlin.Any?): kotlin.String? = if (data is BangumiUserGroup) "$data" else null

        fun decode(data: kotlin.Any?): BangumiUserGroup? = data?.let {
          val normalizedData = "$it".lowercase()
          values().firstOrNull { value ->
            it == value || normalizedData == "$value".lowercase()
          }
        }
    }
}

