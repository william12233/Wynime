@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.models

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class BangumiNextErrorResponse (

    @SerialName(value = "code") @Required val code: kotlin.String,

    @SerialName(value = "error") @Required val error: kotlin.String,

    @SerialName(value = "message") @Required val message: kotlin.String,

    @SerialName(value = "statusCode") @Required val statusCode: kotlin.Int

) {

}

