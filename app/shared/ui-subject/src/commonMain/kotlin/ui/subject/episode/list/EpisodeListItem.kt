package com.wynime.app.ui.subject.episode.list

import androidx.compose.runtime.Immutable
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.subject.preferredDisplayName as subjectPreferredDisplayName
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.random.Random

@Immutable
data class EpisodeListItem(
    val episodeId: Int,
    val sort: EpisodeSort,
    val ep: EpisodeSort?,
    val name: String,
    val nameCn: String,
    val collectionType: UnifiedCollectionType,

    val isBroadcast: Boolean,

    val imageMedium: String? = null,

    val imageLarge: String? = null,

    val playProgress: Float? = null,
) {
    val isDoneOrDropped: Boolean =
        collectionType == UnifiedCollectionType.DONE || collectionType == UnifiedCollectionType.DROPPED

    val displayName: String get() = nameCn.ifBlank { name }

    val nameOrNameCn: String get() = name.ifBlank { nameCn }

    fun preferredDisplayName(useOriginalTitle: Boolean): String =
        if (useOriginalTitle) nameOrNameCn else displayName

    companion object {

        fun from(
            collection: EpisodeCollectionInfo,
            isBroadcast: Boolean,

            playProgress: Float? = null,
        ): EpisodeListItem {
            return EpisodeListItem(
                episodeId = collection.episodeId,
                sort = collection.episodeInfo.sort,
                ep = collection.episodeInfo.ep,
                name = collection.episodeInfo.name,
                nameCn = collection.episodeInfo.nameCn,
                collectionType = collection.collectionType,

                isBroadcast = isBroadcast,
                imageMedium = collection.episodeInfo.imageMedium,
                imageLarge = collection.episodeInfo.imageLarge,
                playProgress = playProgress,
            )
        }
    }
}

@TestOnly
fun createTestEpisodeListItem(
    sort: EpisodeSort = EpisodeSort(1),
    random: Random = Random(sort.hashCode()),
    episodeId: Int = random.nextInt(1, 1000),
    ep: EpisodeSort? = null,
    name: String = "Test Episode $episodeId",
    nameCn: String = "测试剧集 $episodeId",
    collectionType: UnifiedCollectionType = UnifiedCollectionType.entries.random(random),

    isBroadcast: Boolean = random.nextBoolean(),
    imageMedium: String? = null,
    imageLarge: String? = null,
    playProgress: Float? = null,
): EpisodeListItem {
    return EpisodeListItem(
        episodeId,
        sort,
        ep,
        name,
        nameCn,
        collectionType,

        isBroadcast,
        imageMedium,
        imageLarge,
        playProgress,
    )
}

@TestOnly
const val TestEpisodeStillUrl: String = "https://static.myani.org/tmdb/preview/still.jpg"
