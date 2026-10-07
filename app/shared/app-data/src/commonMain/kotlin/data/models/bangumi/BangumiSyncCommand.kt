package com.wynime.app.data.models.bangumi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.wynime.models.CollectionTypeDto
import com.wynime.models.EpisodeCollectionTypeDto
import com.wynime.models.SelfRatingInfoDto
import kotlin.time.Instant

@Serializable
data class BangumiSyncCommand(
    @SerialName(value = "id") val id: String,
    @SerialName(value = "op") val op: BangumiSyncOp?,
    @SerialName(value = "createdAt") val createdAt: Instant,
)

@Serializable
sealed class BangumiSyncOp() {
    @SerialName("UpdateCollection")
    @Serializable
    data class UpdateCollection(
        val subjectId: Long,
        @SerialName("collectionType") val type: CollectionTypeDto?,
        val rating: SelfRatingInfoDto? = null,
    ) : BangumiSyncOp()

    @SerialName("DeleteCollection")
    @Serializable
    data class DeleteCollection(
        val subjectId: Long,
    ) : BangumiSyncOp()

    @SerialName("AddCollection")
    @Serializable
    data class AddCollection(
        val subjectId: Long,
        @SerialName("collectionType") val type: CollectionTypeDto,
        val rating: SelfRatingInfoDto? = null,
    ) : BangumiSyncOp()

    @SerialName("UpdateEpisodeCollection")
    @Serializable
    data class UpdateEpisodeCollection(
        val subjectId: Long,
        val episodeId: Long,
        @SerialName("episodeCollectionType") val type: EpisodeCollectionTypeDto?,
    ) : BangumiSyncOp()
}
