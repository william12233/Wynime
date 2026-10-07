package com.wynime.tools.datasourcetestmcp.video

import kotlinx.serialization.Serializable

@Serializable
data class VideoProbeResult(
    val ok: Boolean,
    val url: String,
    val finalUrl: String? = null,
    val kind: String,
    val statusCode: Int? = null,
    val contentType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val summary: String,
    val playlistEntries: Int? = null,
    val nestedPlaylistUrl: String? = null,
    val sampledSegmentUrl: String? = null,
    val sampledSegmentStatusCode: Int? = null,
    val errors: List<String> = emptyList(),
    val durationMillis: Long? = null,
)

@Serializable
data class ProbeVideoInput(
    val videoUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val probeTimeoutMillis: Long = 15_000,

    val analyze: Boolean = true,

    val playSeconds: Int = 5,

    val playTimeoutMillis: Long = 60_000,

    val showWindow: Boolean = true,

    val detectAds: Boolean = true,

    val captureFramesDir: String? = null,

    val captureAtSeconds: List<Int> = emptyList(),
)

@Serializable
data class ProbeVideoResult(
    val ok: Boolean,
    val summary: String,
    val httpProbe: VideoProbeResult,
    val mediaAnalysis: MediaAnalysisResult? = null,
    val adAnalysis: AdAnalysisResult? = null,

    val capturedFrames: List<CapturedFrame> = emptyList(),
    val errors: List<String> = emptyList(),
    val totalDurationMillis: Long? = null,
)

@Serializable
data class AdAnalysisResult(

    val suspicion: String,
    val reasons: List<String> = emptyList(),

    val playlist: PlaylistAdSignals? = null,

    val hlsFilter: HlsFilterAnalysis? = null,
)

@Serializable
data class HlsFilterAnalysis(

    val status: String,

    val reason: String? = null,

    val filterable: Boolean,
    val mediaPlaylistUrl: String? = null,
    val removedGroups: List<RemovedAdGroup> = emptyList(),
)

@Serializable
data class RemovedAdGroup(

    val reasons: List<String>,
    val segmentCount: Int,
    val durationSeconds: Double,

    val startOffsetSeconds: Double,
    val endOffsetSeconds: Double,
    val startSegmentIndex: Int,
    val endSegmentIndex: Int,
    val firstSegmentUri: String? = null,
)

@Serializable
data class DetectHlsAdsInput(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class DetectHlsAdsResult(
    val ok: Boolean,
    val url: String,

    val summary: String,
    val analysis: AdAnalysisResult,
    val errors: List<String> = emptyList(),
)

@Serializable
data class PlaylistAdSignals(
    val segmentCount: Int,

    val discontinuityCount: Int,

    val distinctSegmentHosts: List<String> = emptyList(),

    val leadingSegmentDurations: List<Double> = emptyList(),
    val medianSegmentDuration: Double? = null,
)

@Serializable
data class CapturedFrame(

    val positionMillis: Long,
    val path: String,

    val label: String,
)

@Serializable
data class MediaAnalysisResult(

    val available: Boolean,
    val tool: String? = null,
    val durationSeconds: Double? = null,

    val overallBitrate: Long? = null,
    val video: VideoStreamInfo? = null,
    val audio: AudioStreamInfo? = null,

    val playback: PlaybackTestResult? = null,
    val errors: List<String> = emptyList(),
)

@Serializable
data class VideoStreamInfo(
    val codec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: String? = null,
    val bitrate: Long? = null,
)

@Serializable
data class AudioStreamInfo(
    val codec: String? = null,
    val sampleRate: String? = null,
    val channels: Int? = null,
    val bitrate: Long? = null,
)

@Serializable
data class PlaybackTestResult(
    val ran: Boolean,
    val ok: Boolean,
    val requestedSeconds: Int? = null,

    val playedPositionMillis: Long? = null,

    val finalState: String? = null,
    val errors: List<String> = emptyList(),

    val openMillis: Long? = null,

    val timeToPlayingMillis: Long? = null,

    val timeToFirstFrameMillis: Long? = null,

    val playWallClockMillis: Long? = null,

    val bufferingCount: Int = 0,

    val bufferingTotalMillis: Long = 0,
)
