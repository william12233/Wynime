package io.github.william12233.wynime

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity(), EventChannel.StreamHandler {
    private companion object {
        const val METHOD_CHANNEL = "io.github.william12233.wynime/media3"
        const val EVENT_CHANNEL = "io.github.william12233.wynime/media3/events"
    }

    private data class TrackDescriptor(
        val id: String,
        val label: String,
        val languageCode: String?,
        val mimeType: String?,
        val isDefault: Boolean,
    )

    private data class BoundTrack(
        val group: TrackGroup,
        val trackIndex: Int,
        val authoritativeId: String,
    )

    private data class NativeTrack(
        val group: Tracks.Group,
        val trackIndex: Int,
        val format: Format,
    )

    private var player: ExoPlayer? = null
    private var eventSink: EventChannel.EventSink? = null
    private var eventSequence = 0L
    private var activeSessionId: String? = null
    private var activeTimelineMapIdentity: String? = null
    private val boundTracks = mutableMapOf<Int, BoundTrack>()

    private val playerListener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> emitState("idle")
                    Player.STATE_BUFFERING -> emitState("buffering")
                    Player.STATE_READY -> emitState(if (player?.isPlaying == true) "playing" else "ready")
                    Player.STATE_ENDED -> emitState("ended")
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (player?.playbackState == Player.STATE_READY) {
                    emitState(if (isPlaying) "playing" else "paused")
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                discardStaleTrackBindings(tracks)
                if (player?.playbackState == Player.STATE_READY) {
                    emitState(if (player?.isPlaying == true) "playing" else "ready")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val httpStatus = findHttpStatus(error)
                val payload = mutableMapOf<String, Any?>(
                    "sequence" to nextSequence(),
                    "state" to "failed",
                    "errorCode" to error.errorCodeName.lowercase(),
                    "httpStatus" to httpStatus,
                    "sessionExpired" to (httpStatus == 401 || httpStatus == 403),
                    "positionMs" to safePosition(),
                    "bufferedPositionMs" to safeBufferedPosition(),
                    "volume" to (player?.volume?.toDouble() ?: 1.0),
                    "rate" to (player?.playbackParameters?.speed?.toDouble() ?: 1.0),
                    "audioTrackId" to selectedTrackId(C.TRACK_TYPE_AUDIO),
                    "subtitleTrackId" to selectedTrackId(C.TRACK_TYPE_TEXT),
                )
                activeSessionId?.let { payload["sessionId"] = it }
                activeTimelineMapIdentity?.let { payload["timelineMapIdentity"] = it }
                eventSink?.success(payload)
            }
        }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, METHOD_CHANNEL)
            .setMethodCallHandler(::handleMethodCall)
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL)
            .setStreamHandler(this)
    }

    private fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "probe" -> result.success(null)
                "open" -> {
                    val sessionId = requiredString(call, "sessionId")
                    val timelineMapIdentity = requiredString(call, "timelineMapIdentity")
                    val uri = validateLoopbackUri(requiredString(call, "uri"))
                    open(sessionId, timelineMapIdentity, uri)
                    result.success(null)
                }
                "play" -> {
                    player?.play()
                    result.success(null)
                }
                "pause" -> {
                    player?.pause()
                    result.success(null)
                }
                "seek" -> {
                    val positionMs = call.argument<Number>("positionMs")?.toLong()
                        ?: throw IllegalArgumentException("positionMs is required")
                    require(positionMs >= 0) { "positionMs must not be negative" }
                    player?.seekTo(positionMs)
                    result.success(null)
                }
                "setVolume" -> {
                    val volume = call.argument<Number>("volume")?.toDouble()
                        ?: throw IllegalArgumentException("volume is required")
                    require(volume.isFinite() && volume in 0.0..1.0) {
                        "volume must be between 0 and 1"
                    }
                    player?.volume = volume.toFloat()
                    emitCurrentState()
                    result.success(null)
                }
                "setRate" -> {
                    val rate = call.argument<Number>("rate")?.toDouble()
                        ?: throw IllegalArgumentException("rate is required")
                    require(rate.isFinite() && rate in 0.25..4.0) {
                        "rate must be between 0.25 and 4"
                    }
                    player?.setPlaybackSpeed(rate.toFloat())
                    emitCurrentState()
                    result.success(null)
                }
                "selectTrack" -> {
                    val type = requiredString(call, "type")
                    val id = call.argument<String>("id")?.trim()?.takeIf { it.isNotEmpty() }
                    val descriptor = id?.let { trackDescriptor(call, it) }
                    selectTrack(type, descriptor)
                    emitCurrentState()
                    result.success(null)
                }
                "close" -> {
                    closePlayer(emitClosed = true)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        } catch (error: IllegalArgumentException) {
            result.error("invalid_media3_request", error.message, null)
        } catch (error: IllegalStateException) {
            result.error("media3_state_error", error.message, null)
        }
    }

    private fun open(sessionId: String, timelineMapIdentity: String, uri: Uri) {
        require(sessionId.matches(Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}$"))) {
            "Invalid sessionId"
        }
        require(
            timelineMapIdentity.length in 1..1024 &&
                timelineMapIdentity.none { it.code < 0x20 || it.code == 0x7f },
        ) {
            "Invalid timelineMapIdentity"
        }
        closePlayer(emitClosed = false)
        activeSessionId = sessionId
        activeTimelineMapIdentity = timelineMapIdentity
        boundTracks.clear()
        emitState("opening")
        try {
            player =
                ExoPlayer.Builder(this).build().also { exoPlayer ->
                    exoPlayer.addListener(playerListener)
                    exoPlayer.setMediaItem(MediaItem.fromUri(uri))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
        } catch (error: RuntimeException) {
            activeSessionId = null
            activeTimelineMapIdentity = null
            boundTracks.clear()
            throw IllegalStateException("Unable to initialize Media3", error)
        }
    }

    private fun selectTrack(type: String, descriptor: TrackDescriptor?) {
        val exoPlayer = player ?: throw IllegalStateException("Media3 player is not open")
        val trackType =
            when (type) {
                "audio" -> C.TRACK_TYPE_AUDIO
                "subtitle" -> C.TRACK_TYPE_TEXT
                else -> throw IllegalArgumentException("Unknown track type")
            }
        val builder =
            exoPlayer.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(trackType)
        if (descriptor == null) {
            boundTracks.remove(trackType)
            exoPlayer.trackSelectionParameters =
                builder.setTrackTypeDisabled(trackType, true).build()
            return
        }
        val match = findUniqueNativeTrack(exoPlayer.currentTracks, trackType, descriptor)
        boundTracks[trackType] =
            BoundTrack(
                group = match.group.mediaTrackGroup,
                trackIndex = match.trackIndex,
                authoritativeId = descriptor.id,
            )
        exoPlayer.trackSelectionParameters =
            builder
                .setTrackTypeDisabled(trackType, false)
                .addOverride(TrackSelectionOverride(match.group.mediaTrackGroup, match.trackIndex))
                .build()
    }

    private fun findUniqueNativeTrack(
        tracks: Tracks,
        trackType: Int,
        descriptor: TrackDescriptor,
    ): NativeTrack {
        val candidates = mutableListOf<NativeTrack>()
        tracks.groups.filter { it.type == trackType }.forEach { group ->
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) {
                    continue
                }
                candidates += NativeTrack(group, trackIndex, group.getTrackFormat(trackIndex))
            }
        }

        val exactId = candidates.filter { it.format.id == descriptor.id }
        if (exactId.size == 1) {
            return exactId.single()
        }
        if (exactId.size > 1) {
            throw IllegalStateException("Requested track ID maps to multiple native tracks")
        }

        val metadataMatches =
            candidates.filter { candidate ->
                val format = candidate.format
                format.label == descriptor.label &&
                    descriptor.languageCode.matchesOptional(format.language) &&
                    descriptor.mimeType.matchesOptional(format.sampleMimeType) &&
                    (!descriptor.isDefault ||
                        (format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0)
            }
        if (metadataMatches.size != 1) {
            throw IllegalStateException(
                if (metadataMatches.isEmpty()) {
                    "Requested authoritative track is unavailable"
                } else {
                    "Requested authoritative track mapping is ambiguous"
                },
            )
        }
        return metadataMatches.single()
    }

    private fun discardStaleTrackBindings(tracks: Tracks) {
        val iterator = boundTracks.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val binding = entry.value
            val stillPresent =
                tracks.groups.any { group ->
                    group.type == entry.key &&
                        group.mediaTrackGroup == binding.group &&
                        binding.trackIndex in 0 until group.length
                }
            if (!stillPresent) {
                iterator.remove()
            }
        }
    }

    private fun closePlayer(emitClosed: Boolean) {
        val closingSessionId = activeSessionId
        val closingTimelineMapIdentity = activeTimelineMapIdentity
        player?.let { existing ->
            existing.removeListener(playerListener)
            existing.stop()
            existing.clearMediaItems()
            existing.release()
        }
        player = null
        activeSessionId = null
        activeTimelineMapIdentity = null
        boundTracks.clear()
        if (emitClosed && closingSessionId != null && closingTimelineMapIdentity != null) {
            emitState("closed", closingSessionId, closingTimelineMapIdentity)
        }
    }

    private fun emitCurrentState() {
        val exoPlayer = player ?: return
        val state =
            when {
                exoPlayer.playbackState == Player.STATE_BUFFERING -> "buffering"
                exoPlayer.playbackState == Player.STATE_ENDED -> "ended"
                exoPlayer.isPlaying -> "playing"
                exoPlayer.playbackState == Player.STATE_READY -> "paused"
                else -> "idle"
            }
        emitState(state)
    }

    private fun emitState(
        state: String,
        sessionId: String? = activeSessionId,
        timelineMapIdentity: String? = activeTimelineMapIdentity,
    ) {
        val payload = mutableMapOf<String, Any?>(
            "sequence" to nextSequence(),
            "state" to state,
            "positionMs" to safePosition(),
            "bufferedPositionMs" to safeBufferedPosition(),
            "volume" to (player?.volume?.toDouble() ?: 1.0),
            "rate" to (player?.playbackParameters?.speed?.toDouble() ?: 1.0),
            "audioTrackId" to selectedTrackId(C.TRACK_TYPE_AUDIO),
            "subtitleTrackId" to selectedTrackId(C.TRACK_TYPE_TEXT),
        )
        sessionId?.let { payload["sessionId"] = it }
        timelineMapIdentity?.let { payload["timelineMapIdentity"] = it }
        eventSink?.success(payload)
    }

    private fun selectedTrackId(trackType: Int): String? {
        val binding = boundTracks[trackType] ?: return null
        val selected =
            player?.currentTracks?.groups?.any { group ->
                group.type == trackType &&
                    group.mediaTrackGroup == binding.group &&
                    binding.trackIndex in 0 until group.length &&
                    group.isTrackSelected(binding.trackIndex)
            } ?: false
        return if (selected) binding.authoritativeId else null
    }

    private fun trackDescriptor(call: MethodCall, id: String): TrackDescriptor {
        require(id.length <= 256 && id.none { it.code < 0x20 || it.code == 0x7f }) {
            "Invalid authoritative track ID"
        }
        val label = requiredString(call, "label")
        require(label.length <= 256 && label.none { it.code < 0x20 || it.code == 0x7f }) {
            "Invalid track label"
        }
        return TrackDescriptor(
            id = id,
            label = label,
            languageCode = optionalBoundedString(call, "languageCode", 64),
            mimeType = optionalBoundedString(call, "mimeType", 128),
            isDefault = call.argument<Boolean>("isDefault") == true,
        )
    }

    private fun optionalBoundedString(call: MethodCall, key: String, maxLength: Int): String? {
        val value = call.argument<String>(key)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        require(value.length <= maxLength && value.none { it.code < 0x20 || it.code == 0x7f }) {
            "Invalid $key"
        }
        return value
    }

    private fun String?.matchesOptional(actual: String?): Boolean {
        if (this == null) {
            return true
        }
        return actual != null && equals(actual, ignoreCase = true)
    }

    private fun nextSequence(): Long = eventSequence++

    private fun safePosition(): Long = player?.currentPosition?.coerceAtLeast(0L) ?: 0L

    private fun safeBufferedPosition(): Long = player?.bufferedPosition?.coerceAtLeast(0L) ?: 0L

    private fun requiredString(call: MethodCall, key: String): String {
        val value = call.argument<String>(key)?.trim()
        require(!value.isNullOrEmpty()) { "$key is required" }
        return value
    }

    private fun validateLoopbackUri(raw: String): Uri {
        val uri = Uri.parse(raw)
        require(uri.scheme == "http") { "Media3 URI must use http loopback proxy" }
        require(uri.host == "127.0.0.1" || uri.host == "::1") {
            "Media3 URI must use a numeric loopback host"
        }
        require(uri.port in 1..65535) { "Media3 URI must include a valid port" }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Media3 URI must not contain user-info, query, or fragment"
        }
        val segments = uri.pathSegments
        require(
            segments.size in 4..16 &&
                segments[0] == "v1" &&
                segments[1] == "session" &&
                segments.all { it.isNotEmpty() && it.length <= 256 },
        ) {
            "Media3 URI must use a bounded session capability path"
        }
        return uri
    }

    private fun findHttpStatus(error: Throwable): Int? {
        var current: Throwable? = error
        repeat(12) {
            val candidate = current
            if (candidate is HttpDataSource.InvalidResponseCodeException) {
                return candidate.responseCode
            }
            current = candidate?.cause
        }
        return null
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        if (player == null) {
            emitState("idle", null, null)
        } else {
            emitCurrentState()
        }
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    override fun onDestroy() {
        closePlayer(emitClosed = false)
        eventSink = null
        super.onDestroy()
    }
}
