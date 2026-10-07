@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextSlimPerson
import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectStaffPosition

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSubjectStaff (

    @SerialName(value = "positions") @Required val positions: kotlin.collections.List<BangumiNextSubjectStaffPosition>,

    @SerialName(value = "staff") @Required val staff: BangumiNextSlimPerson

) {

}

