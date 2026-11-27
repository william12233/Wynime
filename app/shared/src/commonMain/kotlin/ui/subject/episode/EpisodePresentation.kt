package com.wynime.app.ui.subject.episode

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.episode.displayName
import com.wynime.app.data.models.episode.nameOrNameCn
import com.wynime.app.data.models.episode.renderEpisodeEp
import com.wynime.app.data.models.episode.toEpisodeInfo
import com.wynime.app.data.models.subject.SubjectRecurrence
import com.wynime.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.MediaCacheMetadata
import com.wynime.datasources.api.topic.UnifiedCollectionType

@Immutable
data class EpisodePresentation(
    val episodeId: Int,

    val title: String,

    val originalTitle: String = title,

    val ep: String,

    val sort: String,
    val collectionType: UnifiedCollectionType,

    val isKnownBroadcast: Boolean,
    val isPlaceholder: Boolean = false,
    val isDownloaded: Boolean = false,
) {
    companion object {
        @Stable
        val Placeholder = EpisodePresentation(
            episodeId = -1,
            title = "placeholder",
            ep = "placeholder",
            sort = "placeholder",
            collectionType = UnifiedCollectionType.WISH,
            isKnownBroadcast = false,
            isPlaceholder = true,
        )
    }
}

fun EpisodeCollectionInfo.toPresentation(
    recurrence: SubjectRecurrence?,
) = EpisodePresentation(
    episodeId = this.episodeInfo.episodeId,
    title = episodeInfo.displayName,
    originalTitle = episodeInfo.nameOrNameCn,
    ep = episodeInfo.renderEpisodeEp(),
    sort = episodeInfo.sort.toString(),
    collectionType = collectionType,
    isKnownBroadcast = episodeInfo.isKnownCompleted(recurrence),
)

internal fun MediaCacheMetadata.toEpisodePresentation() = EpisodePresentation(
    episodeId = episodeId.toInt(),
    title = episodeName,
    ep = (episodeEp ?: episodeSort).toString().ifBlank { "#$episodeId" },
    sort = episodeSort.toString(),
    collectionType = UnifiedCollectionType.NOT_COLLECTED,
    isKnownBroadcast = true,
)

internal fun mergeEpisodeCatalog(
    local: List<EpisodeCollectionInfo>,
    catalog: List<EpisodeInfo>,
    downloads: List<MediaCacheMetadata>,
    types: List<EpisodeType>,
): List<EpisodeCollectionInfo> {
    val episodes = mutableMapOf<Int, EpisodeCollectionInfo>()
    fun addMetadata(info: EpisodeInfo) {
        episodes[info.episodeId] = EpisodeCollectionInfo(info, UnifiedCollectionType.NOT_COLLECTED)
    }

    val downloadedIds = mutableSetOf<Int>()
    for (download in downloads) {
        val info = download.toEpisodeInfo() ?: continue
        downloadedIds.add(info.episodeId)
        addMetadata(info)
    }
    for (entry in local) {
        val info = entry.episodeInfo
        val cachedTitle = episodes[info.episodeId]?.episodeInfo?.name.orEmpty()
        episodes[info.episodeId] = entry.copy(episodeInfo = info.copy(name = info.name.ifBlank { cachedTitle }))
    }
    for (info in catalog) {
        val existing = episodes[info.episodeId]
        episodes[info.episodeId] = EpisodeCollectionInfo(
            episodeInfo = info.copy(
                name = info.name.ifBlank { existing?.episodeInfo?.name.orEmpty() },
                nameCn = info.nameCn.ifBlank { existing?.episodeInfo?.nameCn.orEmpty() },
                imageMedium = info.imageMedium ?: existing?.episodeInfo?.imageMedium,
                imageLarge = info.imageLarge ?: existing?.episodeInfo?.imageLarge,
            ),
            collectionType = existing?.collectionType ?: UnifiedCollectionType.NOT_COLLECTED,
        )
    }
    return episodes.values.asSequence()
        .filter { episode ->
            val type = episode.episodeInfo.type
            types.isEmpty() || type in types || (type == null && episode.episodeId in downloadedIds)
        }
        .sortedWith(compareBy({ it.episodeInfo.sort }, { it.episodeId }))
        .toList()
}
