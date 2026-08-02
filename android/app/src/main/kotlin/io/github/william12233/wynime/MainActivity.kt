package io.github.william12233.wynime

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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

    private var player: ExoPlayer? = null
    private var eventSink: EventChannel.EventSink? = null
    private var eventSequence = 0L
    private var activeSessionId: String? = null

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
                )
                activeSessionId?.let { payload["sessionId"] = it }
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
                "open" -> {
                    val sessionId = requiredString(call, "sessionId")
                    val uri = validateLoopbackUri(requiredString(call, "uri"))
                    open(sessionId, uri)
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

    private fun open(sessionId: String, uri: Uri) {
        require(sessionId.matches(Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}$"))) {
            "Invalid sessionId"
        }
        closePlayer(emitClosed = false)
        activeSessionId = sessionId
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
            throw IllegalStateException("Unable to initialize Media3", error)
        }
    }

    private fun closePlayer(emitClosed: Boolean) {
        val closingSessionId = activeSessionId
        player?.let { existing ->
            existing.removeListener(playerListener)
            existing.stop()
            existing.clearMediaItems()
            existing.release()
        }
        player = null
        activeSessionId = null
        if (emitClosed) {
            emitState("closed", closingSessionId)
        }
    }

    private fun emitState(state: String, sessionId: String? = activeSessionId) {
        val payload = mutableMapOf<String, Any?>(
            "sequence" to nextSequence(),
            "state" to state,
            "positionMs" to safePosition(),
            "bufferedPositionMs" to safeBufferedPosition(),
        )
        sessionId?.let { payload["sessionId"] = it }
        eventSink?.success(payload)
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
        emitState(if (player == null) "idle" else "ready")
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
