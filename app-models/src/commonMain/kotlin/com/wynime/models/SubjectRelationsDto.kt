@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class SubjectRelationsDto (

    @SerialName(value = "subjectId") @Required val subjectId: kotlin.Long,

    @SerialName(value = "seriesMainSubjectIds") @Required val seriesMainSubjectIds: kotlin.collections.List<kotlin.Int>,

    @SerialName(value = "seriesMainSubjectNames") @Required val seriesMainSubjectNames: kotlin.collections.List<kotlin.String>,

    @SerialName(value = "sequelSubjects") @Required val sequelSubjects: kotlin.collections.List<kotlin.Int>,

    @SerialName(value = "sequelSubjectNames") @Required val sequelSubjectNames: kotlin.collections.List<kotlin.String>

) {

}

