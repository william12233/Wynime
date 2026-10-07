package com.wynime.tools.datasourcetestmcp.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.wynime.tools.datasourcetestmcp.video.DetectHlsAdsInput
import com.wynime.tools.datasourcetestmcp.video.DetectHlsAdsResult
import com.wynime.tools.datasourcetestmcp.video.ProbeVideoInput
import com.wynime.tools.datasourcetestmcp.video.ProbeVideoResult
import com.wynime.tools.datasourcetestmcp.video.VideoService

fun buildToolRegistrations(
    json: Json,
    videoService: VideoService,
): List<McpToolRegistration> = listOf(
    McpToolRegistration(
        McpTool(
            name = "probe_video",
            description = "Probe a final MP4/HLS media URL with the Wynime media pipeline. " +
                    "The result includes HTTP reachability, HLS/MP4 metadata, and optional real mpv playback.",
            inputSchema = objectSchema(required = listOf("videoUrl")) {
                put("videoUrl", stringSchema("Final video URL"))
                put("headers", headersSchema("Request headers such as Referer, Origin, User-Agent, or Cookie"))
                put("probeTimeoutMillis", integerSchema("HTTP probe timeout in milliseconds, default 15000"))
                put("analyze", booleanSchema("Run the real-player playback test, default true"))
                put("playSeconds", integerSchema("Seconds to actually play, default 5"))
                put("playTimeoutMillis", integerSchema("Playback startup/test timeout in milliseconds, default 60000"))
                put("showWindow", booleanSchema("Show a Compose playback window, default true"))
                put("detectAds", booleanSchema("Analyze HLS structure for ad splicing, default true"))
                put("captureFramesDir", stringSchema("Optional directory for playback frame PNGs"))
                put(
                    "captureAtSeconds",
                    buildJsonObject {
                        put("type", "array")
                        put("items", integerSchema("Playback position in seconds"))
                    },
                )
            },
        ),
    ) { args ->
        val input = json.decodeFromJsonElement(ProbeVideoInput.serializer(), args)
        json.encodeToJsonElement(ProbeVideoResult.serializer(), videoService.probeVideo(input))
    },
    McpToolRegistration(
        McpTool(
            name = "detect_hls_ads",
            description = "Inspect an HLS master/media playlist without playing it. " +
                    "Reports structural ad signals and whether the Host HLS filter can remove them.",
            inputSchema = objectSchema(required = listOf("url")) {
                put("url", stringSchema("HLS master or media playlist URL"))
                put("headers", headersSchema("Request headers such as Referer, Origin, User-Agent, or Cookie"))
            },
        ),
    ) { args ->
        val input = json.decodeFromJsonElement(DetectHlsAdsInput.serializer(), args)
        json.encodeToJsonElement(DetectHlsAdsResult.serializer(), videoService.detectHlsAds(input))
    },
)

private inline fun objectSchema(
    required: List<String>,
    properties: JsonObjectBuilder.() -> Unit,
): JsonObject = buildJsonObject {
    put("type", "object")
    if (required.isNotEmpty()) {
        put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
    }
    put("properties", buildJsonObject(properties))
}

private fun stringSchema(description: String): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", JsonPrimitive(description))
}

private fun integerSchema(description: String): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", JsonPrimitive(description))
}

private fun booleanSchema(description: String): JsonObject = buildJsonObject {
    put("type", "boolean")
    put("description", JsonPrimitive(description))
}

private fun headersSchema(description: String): JsonObject = buildJsonObject {
    put("type", "object")
    put("description", JsonPrimitive(description))
    put("additionalProperties", buildJsonObject { put("type", "string") })
}
