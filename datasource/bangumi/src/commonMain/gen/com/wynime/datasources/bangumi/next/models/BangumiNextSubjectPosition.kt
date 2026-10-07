@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectPositionStaff
import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectStaffPositionType

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSubjectPosition (

    @SerialName(value = "position") @Required val position: BangumiNextSubjectStaffPositionType,

    @SerialName(value = "staffs") @Required val staffs: kotlin.collections.List<BangumiNextSubjectPositionStaff>

) {

}

