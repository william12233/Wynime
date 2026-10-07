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

data class BangumiNextWikiPlatform (

    @SerialName(value = "id") @Required val id: kotlin.Int,

    @SerialName(value = "text") @Required val text: kotlin.String,

    @SerialName(value = "wiki_tpl") val wikiTpl: kotlin.String? = null

) {

}

