/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.tools.datasourcetestmcp.video.DetectHlsAdsInput
import me.him188.ani.tools.datasourcetestmcp.video.DetectHlsAdsResult
import me.him188.ani.tools.datasourcetestmcp.video.ProbeVideoInput
import me.him188.ani.tools.datasourcetestmcp.video.ProbeVideoResult
import me.him188.ani.tools.datasourcetestmcp.video.VideoService

/**
 * 通用媒體驗證工具。
 *
 * 來源插件的搜尋、詳情、線路與 resolve 由插件倉庫的 fixture/live 測試負責；這個 MCP 只保留
 * Host 仍可重用的最底層媒體探測與 HLS 分析，不再提供 selector 設定或舊 datasource orchestration。
 */
fun buildToolRegistrations(
    json: Json,
    videoService: VideoService,
): List<McpToolRegistration> = listOf(
    McpToolRegistration(
        McpTool(
            name = "probe_video",
            description = "Probe a final MP4/HLS media URL with the Animeko media pipeline. " +
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
