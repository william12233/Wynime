@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.models

import com.wynime.models.CollectionTypeDto
import com.wynime.models.EpisodeCollectionDto
import com.wynime.models.FavouriteDto
import com.wynime.models.InfoboxDto
import com.wynime.models.SelfRatingInfoDto
import com.wynime.models.SubjectAiringInfoDto
import com.wynime.models.SubjectRelationsDto
import com.wynime.models.SubjectTypeDto
import com.wynime.models.TagDto
import com.wynime.models.TmdbSubjectArtDto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable

data class SubjectCollectionDto (

    @SerialName(value = "id") @Required val id: kotlin.Long,

    @SerialName(value = "type") @Required val type: SubjectTypeDto,

    @SerialName(value = "name") @Required val name: kotlin.String,

    @SerialName(value = "nameCn") @Required val nameCn: kotlin.String,

    @SerialName(value = "summary") @Required val summary: kotlin.String,

    @SerialName(value = "nsfw") @Required val nsfw: kotlin.Boolean,

    @SerialName(value = "airDate") @Required val airDate: kotlin.String,

    @SerialName(value = "aliases") @Required val aliases: kotlin.collections.List<kotlin.String>,

    @SerialName(value = "favorite") @Required val favorite: FavouriteDto,

    @SerialName(value = "tags") @Required val tags: kotlin.collections.List<TagDto>,

    @SerialName(value = "metaTags") @Required val metaTags: kotlin.collections.List<kotlin.String>,

    @SerialName(value = "scoreDetails") @Required val scoreDetails: kotlin.collections.Map<kotlin.String, kotlin.Int>,

    @SerialName(value = "selfRating") @Required val selfRating: SelfRatingInfoDto,

    @SerialName(value = "episodes") @Required val episodes: kotlin.collections.List<EpisodeCollectionDto>,

    @SerialName(value = "relations") @Required val relations: SubjectRelationsDto,

    @SerialName(value = "imageLarge") @Required val imageLarge: kotlin.String,

    @SerialName(value = "imageThumb") @Required val imageThumb: kotlin.String,

    @SerialName(value = "infobox") val infobox: InfoboxDto? = null,

    @SerialName(value = "platform") val platform: kotlin.Int? = null,

    @SerialName(value = "score") val score: kotlin.String? = null,

    @SerialName(value = "rank") val rank: kotlin.Int? = null,

    @SerialName(value = "collectionType") val collectionType: CollectionTypeDto? = null,

    @SerialName(value = "airingInfo") val airingInfo: SubjectAiringInfoDto? = null,

    @SerialName(value = "tmdbArt") val tmdbArt: TmdbSubjectArtDto? = null,

    @SerialName(value = "updatedAt") val updatedAt: kotlin.String? = null

) {

}

