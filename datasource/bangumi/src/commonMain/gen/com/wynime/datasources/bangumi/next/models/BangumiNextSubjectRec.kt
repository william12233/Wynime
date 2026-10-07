@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import com.wynime.datasources.bangumi.next.models.BangumiNextSlimSubject

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextSubjectRec (

    @SerialName(value = "count") @Required val count: kotlin.Int,

    @SerialName(value = "sim") @Required val sim: @Serializable(com.wynime.utils.serialization.BigNumAsDoubleStringSerializer::class) com.wynime.utils.serialization.BigNum,

    @SerialName(value = "subject") @Required val subject: BangumiNextSlimSubject

) {

}

