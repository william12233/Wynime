package com.wynime.app.ui.mediafetch

import com.wynime.app.domain.media.selector.MaybeExcludedMedia
import com.wynime.app.domain.media.selector.UnsafeOriginalMediaAccess
import com.wynime.utils.logging.debug
import com.wynime.utils.logging.logger

object MediaSelectorDebugTools {
    private val logger = logger<MediaSelectorDebugTools>()

    @OptIn(UnsafeOriginalMediaAccess::class)
    fun dumpSubjectNames(filteredCandidates: List<MaybeExcludedMedia>) {
        val result = filteredCandidates
            .map { it.original }
            .distinctBy { it.properties.subjectName }

        logger.debug {
            val joinToString = result.joinToString("\n") { media ->
                media.properties.subjectName?.let { "\"$it\"," }.toString()
            }
            "Dumping subject names: \n\n$joinToString"
        }
    }

    @OptIn(UnsafeOriginalMediaAccess::class)
    fun dumpEpisodeRanges(filteredCandidates: List<MaybeExcludedMedia>) {
        val ranges = filteredCandidates
            .map { it.original }
            .mapNotNull { it.episodeRange }
        logger.debug {
            val joinToString = ranges
                .distinctBy { it.knownSorts.toList() }
                .joinToString("\n") { range ->
                    range.toString()
                }
            "Dumping episode ranges: \n\n$joinToString"
        }
    }
}
