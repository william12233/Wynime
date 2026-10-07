@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextSimpleUser

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextReaction (

    @SerialName(value = "users") @Required val users: kotlin.collections.List<BangumiNextSimpleUser>,

    @SerialName(value = "value") @Required val `value`: kotlin.Int

) {

}

