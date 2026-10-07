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

data class BangumiNextSubjectPlatform (

    @SerialName(value = "alias") @Required val alias: kotlin.String,

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "type") @Required val type: kotlin.String,

    @SerialName(value = "typeCN") @Required val typeCN: kotlin.String,

    @SerialName(value = "enableHeader") val enableHeader: kotlin.Boolean? = null,

    @SerialName(value = "order") val order: kotlin.Int? = null,

    @SerialName(value = "searchString") val searchString: kotlin.String? = null,

    @SerialName(value = "sortKeys") val sortKeys: kotlin.collections.List<kotlin.String>? = null,

    @SerialName(value = "wikiTpl") val wikiTpl: kotlin.String? = null

) {

}

