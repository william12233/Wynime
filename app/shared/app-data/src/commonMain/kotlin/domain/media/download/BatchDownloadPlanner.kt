package com.wynime.app.domain.media.download

import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.topic.contains
import com.wynime.datasources.api.topic.isSingleEpisode
import com.wynime.datasources.api.unwrapCached

internal data class ExistingDownload(

    val origin: Media,
    val episodeId: Int,
)

internal sealed interface EpisodeDownloadPlan {

    data object AlreadyDownloaded : EpisodeDownloadPlan

    data class Reuse(val media: Media) : EpisodeDownloadPlan

    data class Create(val media: Media) : EpisodeDownloadPlan

    data object Uncovered : EpisodeDownloadPlan
}

internal val EpisodeDownloadPlan.mediaOrNull: Media?
    get() = when (this) {
        is EpisodeDownloadPlan.Reuse -> media
        is EpisodeDownloadPlan.Create -> media
        EpisodeDownloadPlan.AlreadyDownloaded, EpisodeDownloadPlan.Uncovered -> null
    }

internal object BatchDownloadPlanner {

    fun plan(
        episodes: List<EpisodeInfo>,
        candidates: List<Media>,
        existing: List<ExistingDownload>,
        pinned: Media? = null,
        pinnedEpisodeId: Int? = null,
    ): Map<Int, EpisodeDownloadPlan> {
        val plan = HashMap<Int, EpisodeDownloadPlan>(episodes.size)
        val downloadedIds = existing.mapTo(HashSet()) { it.episodeId }

        val existingPacks = existing.map { it.origin }.filter { it.isPack() && it.episodeRange?.isKnown == true }.distinctBy { it.mediaId }
        val uncovered = ArrayList<EpisodeInfo>(episodes.size)

        for (episode in episodes) {
            if (episode.episodeId in downloadedIds) {
                plan[episode.episodeId] = EpisodeDownloadPlan.AlreadyDownloaded
                continue
            }
            if (pinned != null && episode.episodeId == pinnedEpisodeId) {
                plan[episode.episodeId] = EpisodeDownloadPlan.Create(pinned)
                continue
            }
            val reusable = existingPacks.firstOrNull { it.covers(episode) }
            if (reusable != null) {
                plan[episode.episodeId] = EpisodeDownloadPlan.Reuse(reusable)
            } else {
                uncovered += episode
            }
        }

        if (pinned != null && pinned.isPack() && pinned.episodeRange?.isKnown == true) {
            uncovered.removeAll { episode ->
                val use = pinned.covers(episode)
                if (use) plan[episode.episodeId] = EpisodeDownloadPlan.Create(pinned)
                use
            }
        }

        val packs = candidates.filter { it.isPack() && it.mediaId != pinned?.mediaId }
        while (uncovered.isNotEmpty()) {
            var best: Media? = null
            var bestCovered: List<EpisodeInfo> = emptyList()
            for (pack in packs) {
                val covered = uncovered.filter { pack.covers(it) }
                if (covered.size < 2) continue
                if (best == null || isBetterPack(pack, covered, best, bestCovered)) {
                    best = pack
                    bestCovered = covered
                }
            }
            val chosen = best ?: break
            for (episode in bestCovered) plan[episode.episodeId] = EpisodeDownloadPlan.Create(chosen)
            uncovered.removeAll(bestCovered.toSet())
        }

        for (episode in uncovered) {
            val media = candidates.firstOrNull { it.isSingle() && it.covers(episode) }
                ?: packs.filter { it.covers(episode) }.maxByOrNull { it.episodeRange?.isKnown == true }
            plan[episode.episodeId] = if (media != null) EpisodeDownloadPlan.Create(media) else EpisodeDownloadPlan.Uncovered
        }

        return episodes.associateTo(LinkedHashMap(episodes.size)) { it.episodeId to plan.getValue(it.episodeId) }
    }

    private fun isBetterPack(
        pack: Media,
        covered: List<EpisodeInfo>,
        best: Media,
        bestCovered: List<EpisodeInfo>,
    ): Boolean {
        val packKnown = pack.episodeRange?.isKnown == true
        val bestKnown = best.episodeRange?.isKnown == true
        if (packKnown != bestKnown) return packKnown
        return covered.size > bestCovered.size
    }

    private fun Media.isPack(): Boolean = episodeRange?.let { !it.isSingleEpisode() } == true

    private fun Media.isSingle(): Boolean = episodeRange?.isSingleEpisode() == true

    private fun Media.covers(episode: EpisodeInfo): Boolean {
        val range = episodeRange ?: return false
        val mainStory = episode.type == EpisodeType.MainStory
        if (range.contains(episode.sort, allowSeason = mainStory, allowSpecial = false)) return true
        return mainStory && episode.ep?.let { range.contains(it, allowSeason = false, allowSpecial = false) } == true
    }

    fun findReusableSeasonMedia(episode: EpisodeInfo, candidates: List<Media>): Media? =
        candidates.firstOrNull { media ->
            val range = media.episodeRange ?: return@firstOrNull false
            !range.isSingleEpisode() &&
                    (episode.ep?.let { range.contains(it) } == true || range.contains(episode.sort))
        }?.unwrapCached()
}
