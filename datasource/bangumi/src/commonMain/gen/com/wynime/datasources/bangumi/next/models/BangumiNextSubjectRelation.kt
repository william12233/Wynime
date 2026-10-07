@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextSlimSubject
import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectRelationType

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSubjectRelation (

    @SerialName(value = "order") @Required val order: kotlin.Int,

    @SerialName(value = "relation") @Required val relation: BangumiNextSubjectRelationType,

    @SerialName(value = "subject") @Required val subject: BangumiNextSlimSubject

) {

}

