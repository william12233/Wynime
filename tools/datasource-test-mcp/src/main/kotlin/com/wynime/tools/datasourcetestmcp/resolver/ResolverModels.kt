package com.wynime.tools.datasourcetestmcp.resolver

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import com.wynime.tools.datasourcetestmcp.video.VideoProbeResult

@Serializable
data class MediaCandidateResult(
    val mediaId: String,
    val mediaSourceId: String,
    val originalTitle: String,
    val originalUrl: String,
    val downloadUri: String,
    val downloadType: String,
    val kind: String,
    val matchKind: String,
    val episodeRange: String? = null,
)

@Serializable
enum class CandidateTestMode {
    @SerialName("all_channels")
    ALL_CHANNELS,

    @SerialName("first_success")
    FIRST_SUCCESS,
}

@Serializable
data class ChannelTestResult(
    val order: Int,
    val candidate: MediaCandidateResult,

    val resolveStatus: String,

    val probeStatus: String,
    val ok: Boolean,
    val summary: String,
    val resolvedVideo: ResolvedVideoResult? = null,
    val probe: VideoProbeResult? = null,
    val resolveDiagnostics: JsonElement? = null,
    val errors: List<String> = emptyList(),

    val resolveDurationMillis: Long? = null,

    val probeDurationMillis: Long? = null,
)

@Serializable
data class ResolvedVideoResult(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val strategy: String,
    val matchedBy: String? = null,
    val pageChain: List<String> = emptyList(),
)
