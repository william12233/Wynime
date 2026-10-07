@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextPersonImages

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSlimCharacter (

    @SerialName(value = "comment") @Required val comment: kotlin.Int,

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "lock") @Required val lock: kotlin.Boolean,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "nameCN") @Required val nameCN: kotlin.String,

    @SerialName(value = "nsfw") @Required val nsfw: kotlin.Boolean,

    @SerialName(value = "role") @Required val role: kotlin.Int,

    @SerialName(value = "images") val images: BangumiNextPersonImages? = null

) {

}

