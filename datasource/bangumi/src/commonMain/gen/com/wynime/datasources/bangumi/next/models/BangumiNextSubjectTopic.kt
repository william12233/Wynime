@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextReply
import com.wynime.datasources.bangumi.next.models.BangumiNextSlimSubject
import com.wynime.datasources.bangumi.next.models.BangumiNextSlimUser

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSubjectTopic (

    @SerialName(value = "content") @Required val content: kotlin.String,

    @SerialName(value = "creator") @Required val creator: BangumiNextSlimUser,

    @SerialName(value = "replies") @Required val replies: kotlin.collections.List<BangumiNextReply>,

    @SerialName(value = "subject") @Required val subject: BangumiNextSlimSubject

) {

}

