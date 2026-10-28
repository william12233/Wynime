package com.wynime.app.videoplayer.player

import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.roundToLong

private val logger = logger<MpvMediampPlayer>()

private const val POLL_INTERVAL_MILLIS = 100L
private const val POSITION_TOLERANCE_MILLIS = 500L
private const val USER_SEEK_DELTA_MILLIS = 1_000L
private const val USER_SEEK_ERROR_MILLIS = 500L
private const val SEEK_TIMEOUT_MILLIS = 3_000L
private const val SEEK_RECENT_GRACE_MILLIS = 750L
private const val RECOVERY_COOLDOWN_MILLIS = 5_000L
private const val RECOVERY_WINDOW_MILLIS = 60_000L
private const val MAX_RECOVERIES_PER_WINDOW = 3

internal enum class MpvDiscontinuityReason {
    TIMELINE_JUMP,
    AV_SYNC_DRIFT,
}

internal data class MpvPlaybackSample(
    val wallTimeMillis: Long,
    val timePosMillis: Long?,
    val avSyncMillis: Long? = null,
    val isReady: Boolean = true,
    val isPlaying: Boolean = true,
    val isBuffering: Boolean = false,
    val isSeeking: Boolean = false,
    val canonicalPositionMillis: Long = timePosMillis ?: 0L,
    val playbackSpeed: Double = 1.0,
    val cacheTimeMillis: Long? = null,
    val cacheSpeedBytesPerSecond: Long? = null,
    val decoderFrameDropCount: Long? = null,
    val videoFrameDropCount: Long? = null,
)

internal data class MpvDiscontinuity(
    val reason: MpvDiscontinuityReason,
    val targetPositionMillis: Long,
    val observedDeltaMillis: Long? = null,
    val expectedDeltaMillis: Long? = null,
    val avSyncMillis: Long? = null,
)

/**
 * Classifies native mpv clock samples. It intentionally ignores frame-drop counters: a late
 * video frame is not enough evidence that the media timeline has jumped.
 */
internal class MpvPlaybackDetector(
    private val timelineJumpThresholdMillis: Long = 750L,
    private val avSyncThresholdMillis: Long = 250L,
    private val avSyncSamplesRequired: Int = 3,
) {
    private var previousSample: MpvPlaybackSample? = null
    private var stablePositionMillis: Long? = null
    private var avSyncViolationCount = 0

    fun reset(positionMillis: Long? = null) {
        previousSample = null
        stablePositionMillis = positionMillis
        avSyncViolationCount = 0
    }

    fun observe(sample: MpvPlaybackSample): MpvDiscontinuity? {
        val positionMillis = sample.timePosMillis
        if (!sample.isReady || positionMillis == null) {
            reset()
            return null
        }

        val previous = previousSample
        previousSample = sample

        if (sample.isBuffering || sample.isSeeking || !sample.isPlaying) {
            previousSample = null
            stablePositionMillis = positionMillis
            avSyncViolationCount = 0
            return null
        }

        if (previous == null || previous.timePosMillis == null) {
            stablePositionMillis = positionMillis
            return detectAvSyncDrift(positionMillis, sample.avSyncMillis)
        }

        val elapsedMillis = sample.wallTimeMillis - previous.wallTimeMillis
        if (elapsedMillis <= 0L || elapsedMillis > 2_000L) {
            stablePositionMillis = positionMillis
            avSyncViolationCount = 0
            return null
        }

        val expectedDeltaMillis = (elapsedMillis * sample.playbackSpeed.coerceIn(0.01, 100.0)).roundToLong()
        val observedDeltaMillis = positionMillis - previous.timePosMillis
        val timelineDeltaErrorMillis = abs(observedDeltaMillis - expectedDeltaMillis)
        if (timelineDeltaErrorMillis >= timelineJumpThresholdMillis &&
            abs(observedDeltaMillis) >= timelineJumpThresholdMillis
        ) {
            return MpvDiscontinuity(
                reason = MpvDiscontinuityReason.TIMELINE_JUMP,
                targetPositionMillis = stablePositionMillis ?: previous.timePosMillis,
                observedDeltaMillis = observedDeltaMillis,
                expectedDeltaMillis = expectedDeltaMillis,
            )
        }

        detectAvSyncDrift(positionMillis, sample.avSyncMillis)?.let { return it }
        stablePositionMillis = positionMillis
        return null
    }

    private fun detectAvSyncDrift(positionMillis: Long, avSyncMillis: Long?): MpvDiscontinuity? {
        if (avSyncMillis != null && abs(avSyncMillis) >= avSyncThresholdMillis) {
            avSyncViolationCount++
        } else {
            avSyncViolationCount = 0
        }
        if (avSyncViolationCount < avSyncSamplesRequired) return null
        return MpvDiscontinuity(
            reason = MpvDiscontinuityReason.AV_SYNC_DRIFT,
            targetPositionMillis = positionMillis,
            avSyncMillis = avSyncMillis,
        )
    }
}

private data class PendingSeek(
    val targetPositionMillis: Long,
    val startedAtMillis: Long,
    val resumeWhenReady: Boolean,
    val retried: Boolean,
)

/**
 * Watches the native mpv clock and repairs bounded playback discontinuities on Windows.
 * Commands still go through Mediamp's state machine; MPVHandle is used only for read-only
 * diagnostics and native clock properties.
 */
internal class MpvPlaybackGuard(
    private val player: MediampPlayer,
    private val handle: MPVHandle,
    parentCoroutineContext: CoroutineContext,
) {
    private val guardJob = SupervisorJob(parentCoroutineContext[Job])
    private val scope = CoroutineScope(parentCoroutineContext + guardJob + player.mainDispatcher)
    private val detector = MpvPlaybackDetector()
    private val recoveryTimes = ArrayDeque<Long>()

    private var previousCanonicalPositionMillis: Long? = null
    private var previousCanonicalSampleAtMillis: Long? = null
    private var pendingSeek: PendingSeek? = null
    private var recentSeekUntilMillis = 0L
    private var lastRecoveryAtMillis = Long.MIN_VALUE

    init {
        scope.launch {
            player.state.collect { state ->
                if (state.mediaStatus == MediaStatus.Released) {
                    scope.cancel()
                }
            }
        }
        scope.launch { monitor() }
    }

    private suspend fun monitor() {
        while (currentCoroutineContext().isActive) {
            if (player.state.value.mediaStatus == MediaStatus.Released) return

            runCatching { readSample() }
                .getOrNull()
                ?.let { sample -> observe(sample) }

            delay(POLL_INTERVAL_MILLIS)
        }
    }

    private fun readSample(): MpvPlaybackSample {
        val state = player.state.value
        return MpvPlaybackSample(
            wallTimeMillis = System.nanoTime() / 1_000_000L,
            timePosMillis = handle.propertyMillisOrNull("time-pos"),
            avSyncMillis = handle.propertyMillisOrNull("avsync"),
            isReady = state.mediaStatus == MediaStatus.Ready,
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering || handle.getPropertyBooleanOrNull("paused-for-cache") == true,
            isSeeking = handle.getPropertyBooleanOrNull("seeking") == true,
            canonicalPositionMillis = player.currentPositionMillis.value,
            playbackSpeed = handle.getPropertyDoubleOrNull("speed")
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?: 1.0,
            cacheTimeMillis = handle.propertyMillisOrNull("demuxer-cache-time"),
            cacheSpeedBytesPerSecond = handle.getPropertyDoubleOrNull("cache-speed")
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?.roundToLong(),
            decoderFrameDropCount = handle.getPropertyInt("decoder-frame-drop-count")
                .takeIf { it >= 0 }
                ?.toLong(),
            videoFrameDropCount = handle.getPropertyInt("frame-drop-count")
                .takeIf { it >= 0 }
                ?.toLong(),
        )
    }

    private suspend fun observe(sample: MpvPlaybackSample) {
        val previousCanonical = previousCanonicalPositionMillis
        val previousCanonicalAtMillis = previousCanonicalSampleAtMillis
        previousCanonicalPositionMillis = sample.canonicalPositionMillis
        previousCanonicalSampleAtMillis = sample.wallTimeMillis

        if (!sample.isReady || sample.timePosMillis == null) {
            previousCanonicalPositionMillis = null
            previousCanonicalSampleAtMillis = null
            pendingSeek = null
            detector.reset()
            return
        }

        val canonicalDelta = previousCanonical?.let { sample.canonicalPositionMillis - it }
        val canonicalElapsedMillis = previousCanonicalAtMillis?.let { sample.wallTimeMillis - it }
        val expectedCanonicalDelta = canonicalElapsedMillis
            ?.takeIf { it in 1..2_000 }
            ?.let { (it * sample.playbackSpeed.coerceIn(0.01, 100.0)).roundToLong() }
        val canonicalTargetChanged = canonicalDelta != null &&
                abs(canonicalDelta) >= USER_SEEK_DELTA_MILLIS &&
                (sample.isSeeking || expectedCanonicalDelta == null ||
                        abs(canonicalDelta - expectedCanonicalDelta) >= USER_SEEK_ERROR_MILLIS)
        if (pendingSeek == null && (sample.isSeeking || canonicalTargetChanged)) {
            pendingSeek = PendingSeek(
                targetPositionMillis = sample.canonicalPositionMillis.coerceAtLeast(0L),
                startedAtMillis = sample.wallTimeMillis,
                resumeWhenReady = sample.isPlaying || player.state.value.playWhenReady,
                retried = false,
            )
        }

        val seek = pendingSeek
        if (seek != null) {
            if (abs(sample.timePosMillis - seek.targetPositionMillis) <= POSITION_TOLERANCE_MILLIS &&
                !sample.isSeeking
            ) {
                pendingSeek = null
                recentSeekUntilMillis = sample.wallTimeMillis + SEEK_RECENT_GRACE_MILLIS
                detector.reset(sample.timePosMillis)
                return
            }

            if (sample.wallTimeMillis - seek.startedAtMillis >= SEEK_TIMEOUT_MILLIS) {
                pendingSeek = null
                if (!seek.retried) {
                    val retry = seek.copy(
                        startedAtMillis = sample.wallTimeMillis,
                        retried = true,
                    )
                    val landed = seekAndWait(retry.targetPositionMillis, retry.resumeWhenReady)
                    detector.reset(runCatching { readTimePositionMillis() }.getOrNull())
                    if (!landed) {
                        logger.warn {
                            "mpv seek did not land after one retry; playback remains paused " +
                                    "target=${retry.targetPositionMillis}"
                        }
                    }
                } else {
                    player.pause()
                    detector.reset(runCatching { readTimePositionMillis() }.getOrNull())
                    logger.warn {
                        "mpv seek remained unresolved after retry; playback remains paused " +
                                "target=${seek.targetPositionMillis}"
                    }
                }
            }
            return
        }

        if (sample.isSeeking || sample.wallTimeMillis < recentSeekUntilMillis) {
            detector.reset(sample.timePosMillis)
            return
        }

        val discontinuity = detector.observe(sample) ?: return
        recoverFrom(discontinuity, sample)
    }

    private suspend fun recoverFrom(discontinuity: MpvDiscontinuity, sample: MpvPlaybackSample) {
        val now = sample.wallTimeMillis
        if (!registerRecovery(now)) {
            detector.reset(sample.timePosMillis)
            logger.warn {
                "mpv discontinuity recovery suppressed by rate limit reason=${discontinuity.reason} " +
                        "position=${sample.timePosMillis}"
            }
            return
        }

        val targetPositionMillis = when (discontinuity.reason) {
            MpvDiscontinuityReason.TIMELINE_JUMP -> discontinuity.targetPositionMillis
            MpvDiscontinuityReason.AV_SYNC_DRIFT -> sample.timePosMillis ?: return
        }.coerceAtLeast(0L)
        val resumeWhenReady = sample.isPlaying || player.state.value.playWhenReady

        logger.warn {
            "mpv discontinuity detected reason=${discontinuity.reason} " +
                    "position=${sample.timePosMillis} target=$targetPositionMillis " +
                    "observedDelta=${discontinuity.observedDeltaMillis} " +
                    "expectedDelta=${discontinuity.expectedDeltaMillis} " +
                    "avSync=${discontinuity.avSyncMillis} " +
                    "cacheTime=${sample.cacheTimeMillis} " +
                    "cacheSpeed=${sample.cacheSpeedBytesPerSecond} " +
                    "decoderDrops=${sample.decoderFrameDropCount} " +
                    "videoDrops=${sample.videoFrameDropCount}"
        }

        val landed = seekAndWait(targetPositionMillis, resumeWhenReady)
        detector.reset(runCatching { readTimePositionMillis() }.getOrNull())
        if (!landed) {
            logger.warn {
                "mpv discontinuity recovery could not reach target=$targetPositionMillis; " +
                        "playback remains paused"
            }
        }
    }

    private suspend fun seekAndWait(targetPositionMillis: Long, resumeWhenReady: Boolean): Boolean {
        val target = clampTargetPosition(targetPositionMillis)
        runCatching {
            player.pause()
            player.seekTo(target)
        }.onFailure {
            logger.warn(it) { "mpv recovery seek command failed; playback remains paused" }
            return false
        }

        val deadline = System.nanoTime() / 1_000_000L + SEEK_TIMEOUT_MILLIS
        while (currentCoroutineContext().isActive && System.nanoTime() / 1_000_000L < deadline) {
            delay(POLL_INTERVAL_MILLIS)
            val position = runCatching { readTimePositionMillis() }.getOrNull()
            val seeking = runCatching { handle.getPropertyBooleanOrNull("seeking") }.getOrNull()
            if (position != null && abs(position - target) <= POSITION_TOLERANCE_MILLIS &&
                seeking == false
            ) {
                if (resumeWhenReady) player.play()
                return true
            }
        }

        player.pause()
        return false
    }

    private fun clampTargetPosition(targetPositionMillis: Long): Long {
        val target = targetPositionMillis.coerceAtLeast(0L)
        val duration = player.mediaProperties.value?.durationMillis ?: return target
        return target.coerceAtMost(duration)
    }

    private fun registerRecovery(now: Long): Boolean {
        while (recoveryTimes.firstOrNull()?.let { now - it >= RECOVERY_WINDOW_MILLIS } == true) {
            recoveryTimes.removeFirst()
        }
        if (recoveryTimes.size >= MAX_RECOVERIES_PER_WINDOW) return false
        if (lastRecoveryAtMillis != Long.MIN_VALUE && now - lastRecoveryAtMillis < RECOVERY_COOLDOWN_MILLIS) {
            return false
        }
        recoveryTimes.addLast(now)
        lastRecoveryAtMillis = now
        return true
    }

    private fun readTimePositionMillis(): Long? = handle.propertyMillisOrNull("time-pos")
}

private fun MPVHandle.propertyMillisOrNull(name: String): Long? {
    return getPropertyDoubleOrNull(name)
        ?.takeIf { it.isFinite() }
        ?.times(1_000.0)
        ?.roundToLong()
}
