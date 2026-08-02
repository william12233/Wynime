package io.github.william12233.wynime

import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

internal class MpvPrototypeBridge(messenger: BinaryMessenger) : EventChannel.StreamHandler {
    private companion object {
        const val METHOD_CHANNEL = "io.github.william12233.wynime/mpv"
        const val EVENT_CHANNEL = "io.github.william12233.wynime/mpv/events"
        const val BACKEND_ID = "android-mpv"
    }

    private val methodChannel = MethodChannel(messenger, METHOD_CHANNEL)
    private val eventChannel = EventChannel(messenger, EVENT_CHANNEL)
    private var eventSink: EventChannel.EventSink? = null
    private var eventSequence = 0L

    init {
        methodChannel.setMethodCallHandler(::handleMethodCall)
        eventChannel.setStreamHandler(this)
    }

    fun dispose() {
        eventSink = null
        eventChannel.setStreamHandler(null)
        methodChannel.setMethodCallHandler(null)
    }

    private fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "probe" -> result.success(AndroidMpvRuntimeProbe.probe())
            "open", "pause", "seek", "close" ->
                result.error(
                    "android_mpv_unavailable",
                    "Phase 6 does not bundle the Android libmpv JNI bridge.",
                    AndroidMpvRuntimeProbe.probe(),
                )
            else -> result.notImplemented()
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        eventSink?.success(
            mapOf(
                "sequence" to eventSequence++,
                "state" to "idle",
            ),
        )
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    private object AndroidMpvRuntimeProbe {
        @Volatile
        private var cached: Map<String, Any?>? = null

        fun probe(): Map<String, Any?> =
            cached ?: synchronized(this) {
                cached ?: runProbe().also { cached = it }
            }

        private fun runProbe(): Map<String, Any?> {
            try {
                System.loadLibrary("mpv")
            } catch (_: UnsatisfiedLinkError) {
                return capability(
                    availability = "unavailable",
                    code = "runtime_missing",
                )
            } catch (_: SecurityException) {
                return capability(
                    availability = "unavailable",
                    code = "runtime_load_denied",
                )
            }

            return try {
                System.loadLibrary("wynime_mpv_bridge")
                capability(
                    availability = "available",
                    code = "jni_bridge_ready",
                )
            } catch (_: UnsatisfiedLinkError) {
                capability(
                    availability = "incompatible",
                    code = "jni_bridge_missing",
                )
            } catch (_: SecurityException) {
                capability(
                    availability = "incompatible",
                    code = "jni_bridge_load_denied",
                )
            }
        }

        private fun capability(
            availability: String,
            code: String,
        ): Map<String, Any?> =
            mapOf(
                "backendId" to BACKEND_ID,
                "availability" to availability,
                "code" to code,
            )
    }
}
