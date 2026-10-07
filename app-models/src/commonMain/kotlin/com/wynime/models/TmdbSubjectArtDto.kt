@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import com.wynime.models.TmdbImageDto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class TmdbSubjectArtDto (

    @SerialName(value = "backdrops") @Required val backdrops: kotlin.collections.List<TmdbImageDto>,

    @SerialName(value = "posters") @Required val posters: kotlin.collections.Map<kotlin.String, TmdbImageDto>,

    @SerialName(value = "logos") @Required val logos: kotlin.collections.Map<kotlin.String, TmdbImageDto>

) {

}

