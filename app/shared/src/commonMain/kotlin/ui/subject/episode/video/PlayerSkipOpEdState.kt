package com.wynime.app.ui.subject.episode.video

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.domain.media.player.prefetch.MediaPrefetchRequest
import com.wynime.app.domain.media.player.prefetch.MediaTimeRange
import org.openani.mediamp.metadata.Chapter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal val DEFAULT_OP_ED_SKIP_DURATION = VideoScaffoldConfig.Default.opEdSkipDuration

@Stable
class PlayerSkipOpEdState(
    chapters: State<List<Chapter>>,
    private val onSkip: (targetMillis: Long) -> Unit,
    videoLength: State<Duration>,
) {
    private var currentChapter: CurrentChapter? by mutableStateOf(null)
    private val opEdChapters by derivedStateOf {
        chapters.value.filter {
            OpEdLength.fromVideoLengthOrNull(videoLength.value)
                ?.isOpEdChapter(it.durationMillis.milliseconds) == true
        }.map { CurrentChapter(chapter = it, false) }
    }

    val skipped: Boolean by derivedStateOf {
        currentChapter?.skipped ?: false
    }

    val pendingChapter: Chapter? by derivedStateOf { currentChapter?.takeUnless { it.skipped }?.chapter }

    val showSkipTips: Boolean by derivedStateOf {
        currentChapter != null && !skipped
    }

    var prefetchRequest: MediaPrefetchRequest? by mutableStateOf(null)
        private set

    fun cancelSkipOpEd() {
        currentChapter?.skipped = true
    }

    fun update(currentPos: Long) {
        prefetchRequest = computePrefetchRequest(currentPos)
        if (opEdChapters.isEmpty()) return

        opEdChapters.find { it.chapter.offsetMillis in currentPos - 1000..currentPos + 5000 }?.let {
            if (currentChapter == null) {
                currentChapter = it
            }
        } ?: run {
            currentChapter?.skipped = true
            currentChapter = null
        }

        currentChapter?.takeIf { it.chapter.offsetMillis in currentPos - 1000..currentPos }?.run {
            if (skipped) return@run
            onSkip(chapter.offsetMillis + chapter.durationMillis)
            skipped = true
            currentChapter = null
        }
    }

    private fun computePrefetchRequest(currentPos: Long): MediaPrefetchRequest? {
        val upcoming = opEdChapters.firstOrNull {
            val start = it.chapter.offsetMillis
            val end = start + it.chapter.durationMillis

            !it.skipped && currentPos >= start - PREFETCH_LEAD_MILLIS && currentPos < end
        } ?: return null
        val start = upcoming.chapter.offsetMillis
        val end = start + upcoming.chapter.durationMillis
        return MediaPrefetchRequest(
            range = MediaTimeRange(end, end + PREFETCH_DURATION_MILLIS),
            requireBufferedUntilMillis = start,
        )
    }

    companion object {

        const val PREFETCH_LEAD_MILLIS: Long = 30_000

        const val PREFETCH_DURATION_MILLIS: Long = 30_000
    }
}

@Stable
class CurrentChapter(val chapter: Chapter, skipped: Boolean) {
    var skipped by mutableStateOf(skipped)
}

fun interface OpEdLength {
    fun isOpEdChapter(chapterLength: Duration): Boolean

    companion object {
        private val Normal = OpEdLength { it in 80.seconds..95.seconds }
        private val Short = OpEdLength { it in 55.seconds..65.seconds }

        fun fromVideoLengthOrNull(length: Duration): OpEdLength? {
            return when {
                length > 20.minutes -> Normal
                length > 10.minutes -> Short
                else -> null
            }
        }
    }
}
