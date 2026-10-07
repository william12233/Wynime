@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextSlimBlogEntry
import com.wynime.datasources.bangumi.next.models.BangumiNextSlimUser

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSubjectReview (

    @SerialName(value = "entry") @Required val entry: BangumiNextSlimBlogEntry,

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "user") @Required val user: BangumiNextSlimUser

) {

}

