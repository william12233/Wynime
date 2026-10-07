@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.models

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiIndexSubjectAddInfo (

    @SerialName(value = "subject_id") val subjectId: kotlin.Int? = null,

    @SerialName(value = "sort") val sort: kotlin.Int? = null,

    @SerialName(value = "comment") val comment: kotlin.String? = null

) {

}

