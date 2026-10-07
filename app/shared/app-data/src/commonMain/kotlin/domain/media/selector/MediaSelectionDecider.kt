package com.wynime.app.domain.media.selector

import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaPreference.Companion.ANY_FILTER
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaSourceKind

internal object MediaSelectionDecider {
    fun findByPreference(
        candidates: List<MaybeExcludedMedia.Included>,
        mergedPreference: MediaPreference,
        availableAlliances: List<String>,
        mediaSelectorContext: MediaSelectorContext,
        mediaSelectorSettings: MediaSelectorSettings,
    ): Media? {
        val selectedSubtitleLanguageId = mergedPreference.subtitleLanguageId
        val selectedResolution = mergedPreference.resolution
        val selectedAlliance = mergedPreference.alliance
        val selectedMediaSource = mergedPreference.mediaSourceId
        val allianceRegexes = mergedPreference.alliancePatterns.orEmpty().map { it.toRegex() }
        val preferKind = mediaSelectorSettings.preferKind

        val languageIds = sequence {
            selectedSubtitleLanguageId?.let {
                yield(it)
                return@sequence
            }
            yieldAll(mergedPreference.fallbackSubtitleLanguageIds.orEmpty())
        }
        val resolutions = sequence {
            selectedResolution?.let {
                yield(it)
                return@sequence
            }
            yieldAll(mergedPreference.fallbackResolutions.orEmpty())
        }
        val alliances = sequence {
            selectedAlliance?.let {
                yield(it)
                return@sequence
            }
            if (allianceRegexes.isEmpty()) {
                yield(ANY_FILTER)
            } else {
                for (regex in allianceRegexes) {
                    for (alliance in availableAlliances) {

                        if (regex.find(alliance) != null) yield(alliance)
                    }
                }
            }
        }
        val mediaSources = sequence {
            selectedMediaSource?.let {
                yield(it)
                return@sequence
            }
            val fallback = mediaSelectorContext.mediaSourcePrecedence
            if (fallback != null) {
                yieldAll(fallback)
            }
            yield(null)
        }

        fun selectAny(list: List<Media>): Media? {
            if (list.isEmpty()) {
                return null
            }
            return list.first()
        }

        fun selectAny(candidates: List<MaybeExcludedMedia.Included>) =
            selectAny(candidates.map { it.result })

        fun selectImpl(candidates: List<Media>): Media? {
            for (resolution in resolutions) {
                val filteredByResolution =
                    if (resolution == ANY_FILTER) candidates
                    else candidates.filter { resolution == it.properties.resolution }
                if (filteredByResolution.isEmpty()) continue

                for (languageId in languageIds) {
                    val filteredByLanguage =
                        if (languageId == ANY_FILTER) filteredByResolution
                        else filteredByResolution.filter { languageId in it.properties.subtitleLanguageIds }
                    if (filteredByLanguage.isEmpty()) continue

                    for (alliance in alliances) {

                        val filteredByAlliance =
                            if (alliance == ANY_FILTER) filteredByLanguage
                            else filteredByLanguage.filter { alliance == it.properties.alliance }
                        if (filteredByAlliance.isEmpty()) continue

                        for (mediaSource in mediaSources) {
                            val filteredByMediaSource =
                                if (mediaSource == ANY_FILTER) filteredByAlliance
                                else filteredByAlliance.filter {
                                    mediaSource == null || mediaSource == it.mediaSourceId
                                }
                            if (filteredByMediaSource.isEmpty()) continue
                            return selectAny(filteredByMediaSource)
                        }
                    }

                    for (mediaSource in mediaSources) {
                        val filteredByMediaSource =
                            if (mediaSource == ANY_FILTER) filteredByLanguage
                            else filteredByLanguage.filter {
                                mediaSource == null || mediaSource == it.mediaSourceId
                            }
                        if (filteredByMediaSource.isEmpty()) continue
                        return selectAny(filteredByMediaSource)
                    }
                }

            }
            return null
        }

        fun selectImpl(maybeExcludedMedia: List<MaybeExcludedMedia.Included>) =
            selectImpl(maybeExcludedMedia.map { it.result })

        if (preferKind != null) {
            val preferred = candidates.filter { it.result.kind == preferKind }
            if (preferKind == MediaSourceKind.WEB) {

                selectImpl(preferred.filter { it.similarity > 80 })?.let {
                    return it
                }
            }
            selectImpl(preferred)?.let {
                return it
            }
        }

        selectImpl(candidates)?.let { return it }
        return selectAny(candidates)
    }
}
