@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import com.wynime.datasources.bangumi.models.BangumiSlimSubject
import com.wynime.datasources.bangumi.models.BangumiSubjectCollectionType
import com.wynime.datasources.bangumi.models.BangumiSubjectType

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiUserSubjectCollection (

    @SerialName(value = "subject_id") @Required val subjectId: kotlin.Int,

    @SerialName(value = "subject_type") @Required val subjectType: BangumiSubjectType,

    @SerialName(value = "rate") @Required val rate: kotlin.Int,

    @SerialName(value = "type") @Required val type: BangumiSubjectCollectionType,

    @SerialName(value = "tags") @Required val tags: kotlin.collections.List<kotlin.String>,

    @SerialName(value = "ep_status") @Required val epStatus: kotlin.Int,

    @SerialName(value = "vol_status") @Required val volStatus: kotlin.Int,

    @SerialName(value = "updated_at") @Required val updatedAt: kotlinx.datetime.Instant,

    @SerialName(value = "private") @Required val `private`: kotlin.Boolean,

    @SerialName(value = "comment") val comment: kotlin.String? = null,

    @SerialName(value = "subject") val subject: BangumiSlimSubject? = null

) {

}

