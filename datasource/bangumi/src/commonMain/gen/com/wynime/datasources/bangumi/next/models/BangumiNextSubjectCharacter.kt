@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextSlimCharacter
import com.wynime.datasources.bangumi.next.models.BangumiNextSlimPerson

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSubjectCharacter (

    @SerialName(value = "actors") @Required val actors: kotlin.collections.List<BangumiNextSlimPerson>,

    @SerialName(value = "character") @Required val character: BangumiNextSlimCharacter,

    @SerialName(value = "order") @Required val order: kotlin.Int,

    @SerialName(value = "type") @Required val type: kotlin.Int

) {

}

