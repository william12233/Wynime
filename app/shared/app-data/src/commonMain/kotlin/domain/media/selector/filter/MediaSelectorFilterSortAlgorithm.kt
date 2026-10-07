package com.wynime.app.domain.media.selector.filter

import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaPreference.Companion.ANY_FILTER
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.domain.media.selector.MatchMetadata
import com.wynime.app.domain.media.selector.MaybeExcludedMedia
import com.wynime.app.domain.media.selector.MediaExclusionReason
import com.wynime.app.domain.media.selector.MediaSelectorContext
import com.wynime.app.domain.media.selector.SubtitleKindPreference
import com.wynime.app.domain.media.selector.UnsafeOriginalMediaAccess
import com.wynime.app.domain.mediasource.MediaListFilter
import com.wynime.app.domain.mediasource.MediaListFilterContext
import com.wynime.app.domain.mediasource.MediaListFilters
import com.wynime.app.domain.mediasource.StringMatcher
import com.wynime.app.domain.mediasource.asCandidate
import com.wynime.app.domain.mediasource.MediaSourceTier
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.isLocalCache
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.contains
import com.wynime.utils.coroutines.flows.sequenceOfEmptyString
import com.wynime.datasources.api.CachedMedia
import com.wynime.app.data.models.episode.displayName

class MediaSelectorFilterSortAlgorithm {

    fun filterMediaList(
        list: List<Media>,
        preference: MediaPreference,
        settings: MediaSelectorSettings,
        context: MediaSelectorContext,
    ): List<MaybeExcludedMedia> = filterMediaList(list, preference, settings, context, matchEpisode = true)

    fun filterMediaListForSubject(
        list: List<Media>,
        preference: MediaPreference,
        settings: MediaSelectorSettings,
        context: MediaSelectorContext,
    ): List<MaybeExcludedMedia> = filterMediaList(list, preference, settings, context, matchEpisode = false)

    private fun filterMediaList(
        list: List<Media>,
        preference: MediaPreference,
        settings: MediaSelectorSettings,
        context: MediaSelectorContext,
        matchEpisode: Boolean,
    ): List<MaybeExcludedMedia> {
        val subjectInfo = context.subjectInfo?.takeIf { info ->
            info != SubjectInfo.Empty && info.allNames.any { it.isNotBlank() }
        }
        val episodeInfo = context.episodeInfo.takeIf { context.hasEpisode }

        val mediaListFilterContext = if (subjectInfo != null && episodeInfo != null) {
            MediaListFilterContext(
                subjectNames = subjectInfo.allNames.toSet(),
                episodeSort = episodeInfo.sort,
                episodeEp = episodeInfo.ep,
                episodeName = episodeInfo.name,
            )
        } else null

        val episodeMatch = if (matchEpisode && episodeInfo != null) {
            EpisodeMatch(
                episodeId = episodeInfo.episodeId,
                sort = episodeInfo.sort,
                ep = episodeInfo.ep,
                name = episodeInfo.displayName,

                acceptOva = context.subjectInfo?.allNames.orEmpty().any { it.matches(REGEX_OVA_TAILING) },
            )
        } else null

        return list.map { media ->
            filterMedia(media, preference, settings, context, mediaListFilterContext, episodeMatch)
        }
    }

    private class EpisodeMatch(
        private val episodeId: Int,
        private val sort: EpisodeSort,
        private val ep: EpisodeSort?,
        name: String,
        private val acceptOva: Boolean,
    ) {

        private val nameForSpecial: String? = name.trim().takeIf { sort !is EpisodeSort.Normal && it.length >= MIN_SPECIAL_NAME_LENGTH }

        fun matches(media: Media): Boolean {
            val range = media.episodeRange
            if (media.isLocalCache()) {
                val cacheEpisodeId = (media as? CachedMedia)?.cacheEpisodeId
                if (!cacheEpisodeId.isNullOrEmpty() && episodeId != 0) return cacheEpisodeId == episodeId.toString()
                return range != null && (
                        range.contains(sort, allowSeason = false, allowSpecial = false) ||
                                (ep != null && range.contains(ep, allowSeason = false, allowSpecial = false)))
            }
            if (range != null) {
                if (range.contains(sort)) return true
                if (ep != null && range.contains(ep)) return true
                if (acceptOva && range.knownSorts.any { it is EpisodeSort.Special && it.type == EpisodeType.OVA }) return true
            }
            if (nameForSpecial != null && MediaListFilters.specialContains(media.originalTitle, nameForSpecial)) return true
            return false
        }
    }

    @Suppress("PrivatePropertyName")
    private val SEASON_TAILING = Regex("""第\s*(?<season>.+)\s*[部季]""")

    @Suppress("PrivatePropertyName")
    private val REGEX_OVA_TAILING = Regex(".+OVA\\s*\\d*$", RegexOption.IGNORE_CASE)

    private fun filterMedia(
        media: Media,
        preference: MediaPreference,
        settings: MediaSelectorSettings,
        context: MediaSelectorContext,
        mediaListFilterContext: MediaListFilterContext?,
        episodeMatch: EpisodeMatch?,
    ): MaybeExcludedMedia {
        val mediaSubjectName = media.properties.subjectName
        val mediaSubjectNameOrOriginalTitle = mediaSubjectName ?: media.originalTitle
        val contextSubjectNames = context.subjectInfo?.allNames.orEmpty().asSequence()

        fun include(): MaybeExcludedMedia {
            return MaybeExcludedMedia.Included(
                media,
                metadata = calculateMatchMetadata(
                    contextSubjectNames,
                    mediaSubjectNameOrOriginalTitle,
                    media.episodeRange,
                    context.episodeInfo?.sort,
                    context.episodeInfo?.ep,
                ),
            )
        }

        fun exclude(reason: MediaExclusionReason): MaybeExcludedMedia = MaybeExcludedMedia.Excluded(media, reason)

        if (episodeMatch != null && !episodeMatch.matches(media)) {
            return exclude(MediaExclusionReason.EpisodeMismatch(media.episodeRange))
        }

        if (media.isLocalCache()) return include()

        if (!preference.showWithoutSubtitle &&
            (media.properties.subtitleLanguageIds.isEmpty() && media.extraFiles.subtitles.isEmpty())
        ) {

            return exclude(MediaExclusionReason.MediaWithoutSubtitle)
        }

        val subtitleKind = media.properties.subtitleKind
        if (context.subtitlePreferences != null && subtitleKind != null) {
            if (context.subtitlePreferences[subtitleKind] == SubtitleKindPreference.HIDE) {
                return exclude(MediaExclusionReason.UnsupportedByPlatformPlayer)
            }
        }

        if (mediaSubjectName != null) {

            if (contextSubjectNames.any { contextSubjectNames ->
                    MediaListFilters.specialEquals(mediaSubjectName, contextSubjectNames)
                }) {

            } else {

                val mediaSubjectNameSeasonSimplified = mediaSubjectName.replace(SEASON_TAILING, $$"${season}")
                context.subjectSeriesInfo?.seriesSubjectNamesWithoutSelf?.forEach { name ->
                    if (MediaListFilters.specialEquals(mediaSubjectName, name) ||
                        MediaListFilters.specialEquals(mediaSubjectNameSeasonSimplified, name)
                    ) {

                        return if (name in context.subjectSeriesInfo.sequelSubjectNames) {
                            exclude(MediaExclusionReason.FromSequelSeason)
                        } else {
                            exclude(MediaExclusionReason.FromSeriesSeason)
                        }
                    }
                }

            }
        } else {

            context.subjectSeriesInfo?.sequelSubjectNames?.forEach { sequelName ->
                if (sequelName.isNotBlank() &&

                    mediaSubjectNameOrOriginalTitle.contains(sequelName, ignoreCase = true)
                ) {

                    return exclude(MediaExclusionReason.FromSeriesSeason)
                }
            }
        }

        if (mediaListFilterContext != null) {
            val allow = when (media.kind) {
                MediaSourceKind.WEB -> {
                    with(MediaListFilters.ContainsSubjectName) {
                        val baseContains = mediaListFilterContext.applyOn(
                            object : MediaListFilter.Candidate by media.asCandidate() {
                                override val subjectName: String get() = mediaSubjectNameOrOriginalTitle
                            },
                        )
                        if (media.episodeRange?.contains(EpisodeSort("OVA")) == true) {

                            val propName = media.properties.subjectName ?: return@with false
                            val ovaContains = mediaListFilterContext.applyOn(
                                object : MediaListFilter.Candidate by media.asCandidate() {
                                    override val subjectName: String get() = "$propName OVA"
                                },
                            )
                            baseContains || ovaContains
                        } else {
                            baseContains
                        }
                    }
                }

                MediaSourceKind.LocalCache -> true
            }

            if (!allow) {
                return exclude(MediaExclusionReason.SubjectNameMismatch)
            }
        }

        return include()
    }

    private fun calculateMatchMetadata(
        contextSubjectNames: Sequence<String>,
        mediaSubjectName: String,
        mediaEpisodeRange: EpisodeRange?,
        contextEpisodeSort: EpisodeSort?,
        contextEpisodeEp: EpisodeSort?
    ) = MatchMetadata(
        subjectMatchKind = if (
            contextSubjectNames.any {
                MediaListFilters.specialEquals(mediaSubjectName, it)
            }
        ) {
            MatchMetadata.SubjectMatchKind.EXACT
        } else {
            MatchMetadata.SubjectMatchKind.FUZZY
        },
        episodeMatchKind = if (mediaEpisodeRange != null) {
            when {
                contextEpisodeSort != null && contextEpisodeSort in mediaEpisodeRange -> {
                    MatchMetadata.EpisodeMatchKind.SORT
                }

                contextEpisodeEp != null && contextEpisodeEp in mediaEpisodeRange -> {
                    MatchMetadata.EpisodeMatchKind.EP
                }

                else -> {
                    MatchMetadata.EpisodeMatchKind.NONE
                }
            }
        } else {
            MatchMetadata.EpisodeMatchKind.NONE
        },
        similarity = (contextSubjectNames + sequenceOfEmptyString())
            .map { StringMatcher.calculateMatchRate(it, mediaSubjectName) }
            .max(),
    )

    @OptIn(UnsafeOriginalMediaAccess::class)
    fun sortMediaList(
        list: List<MaybeExcludedMedia>,
        settings: MediaSelectorSettings,
        context: MediaSelectorContext,
    ): List<MaybeExcludedMedia> {
        return list.sortedWith(

            compareBy<MaybeExcludedMedia> { 0 }

                .thenBy { maybe ->
                    when (maybe) {
                        is MaybeExcludedMedia.Included -> 0
                        is MaybeExcludedMedia.Excluded -> 1
                    }
                }

                .thenBy { maybe ->
                    val subtitleKind = maybe.original.properties.subtitleKind
                    if (context.subtitlePreferences != null && subtitleKind != null) {
                        if (context.subtitlePreferences[subtitleKind] != SubtitleKindPreference.NORMAL) {
                            return@thenBy 1
                        }
                    }
                    0
                }

                .thenByDescending { maybe ->
                    when (maybe.original.kind) {

                        MediaSourceKind.LocalCache -> {
                            2
                        }

                        MediaSourceKind.WEB -> {
                            if (settings.preferKind == null) {
                                0
                            } else {
                                if (maybe.original.kind == settings.preferKind) {
                                    1
                                } else {
                                    0
                                }
                            }
                        }
                    }
                }
                .then(
                    compareBy { it.original.costForDownload },
                )
                .thenBy { maybe ->
                    val tiers = context.mediaSourceTiers

                    tiers?.get(maybe.original.mediaSourceId, maybe.original.properties.alliance)
                        ?: MediaSourceTier.MaximumValue
                }
                .thenByDescending {
                    it.original.publishedTime
                }
                .thenByDescending {

                    when (it) {
                        is MaybeExcludedMedia.Excluded -> 0
                        is MaybeExcludedMedia.Included -> it.similarity
                    }
                },
        )
    }

    private val Media.costForDownload
        get() = when (location) {
            MediaSourceLocation.Local -> 0
            MediaSourceLocation.Lan -> 1
            else -> 2
        }

    fun filterByPreference(
        mediaList: List<MaybeExcludedMedia>,
        mergedPreferences: MediaPreference,
    ): List<MaybeExcludedMedia> {
        infix fun <Pref : Any> Pref?.matches(prop: Pref): Boolean =
            this == null || this == prop || this == ANY_FILTER

        infix fun <Pref : Any> Pref?.matches(prop: List<Pref>): Boolean =
            this == null || this in prop || this == ANY_FILTER

        fun filterCandidate(it: Media): Boolean {
            if (it.isLocalCache()) {
                return true
            }

            return mergedPreferences.alliance matches it.properties.alliance &&
                    mergedPreferences.resolution matches it.properties.resolution &&
                    mergedPreferences.subtitleLanguageId matches it.properties.subtitleLanguageIds &&
                    mergedPreferences.mediaSourceId matches it.mediaSourceId
        }

        return mediaList.filter {
            @OptIn(UnsafeOriginalMediaAccess::class)
            filterCandidate(it.original)
        }
    }
}

private const val MIN_SPECIAL_NAME_LENGTH = 3
