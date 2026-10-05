/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.topic

import kotlinx.serialization.Serializable


@Serializable
sealed class ResourceLocation {
    abstract val uri: String

    /**
     * 流式传输视频文件, 例如 m3u8
     * `*.mkv`, `*.mp4` form `http://`, `https://`.
     */
    @Serializable
    data class HttpStreamingFile(override val uri: String) : ResourceLocation() {
        init {
            require(
                uri.startsWith("https://") ||
                        uri.startsWith("http://") ||
                        uri.startsWith("file://"),
            ) {
                "HttpStreamingFile uri must start with 'http://' or 'https://', but was $uri"
            }
        }
    }

    /**
     * 需要 WebView 去里面解析视频链接
     */
    @Serializable
    data class WebVideo(
        /**
         * Web 页面地址
         */
        override val uri: String,
        /** Headers required for the initial page request, such as Referer or a short-lived token. */
        val headers: Map<String, String> = emptyMap(),
    ) : ResourceLocation() {
        init {
            require(uri.startsWith("https://") || uri.startsWith("http://")) {
                "WebVideo uri must start with 'http://' or 'https://', but was $uri"
            }
        }
    }

    /**
     * A stable reference to a runtime source plugin. The plugin resolves the short-lived media
     * URL only when playback or download starts.
     */
    @Serializable
    data class SourcePluginMedia(
        val pluginId: String,
        val subjectId: String,
        val channelId: String,
        val episodeId: String,
        /** Original page URL, used for diagnostics and browser fallback. */
        override val uri: String,
        /** Short-lived discovery trace association; it is not part of the stable media identity. */
        val traceId: String = "",
    ) : ResourceLocation() {
        init {
            require(pluginId.isNotBlank()) { "pluginId must not be blank" }
            require(subjectId.isNotBlank()) { "subjectId must not be blank" }
            require(channelId.isNotBlank()) { "channelId must not be blank" }
            require(episodeId.isNotBlank()) { "episodeId must not be blank" }
            require(uri.isNotBlank()) { "SourcePluginMedia uri must not be blank" }
        }
    }

    /**
     * 本地文件路径
     */
    @Serializable
    data class LocalFile(
        val filePath: String, // absolute
        /**
         * Hint to help the player to determine the file type.
         *
         * `null` for unknown.
         */
        val fileType: FileType? = null,
        /**
         * m3u8 原始地址.
         */
        val originalUri: String? = null,
    ) : ResourceLocation() {
        /**
         * `file://`
         */
        override val uri: String by lazy {
            "file://${filePath}"
        }

        @Serializable
        enum class FileType {
            /**
             *  MPEG Transport Stream
             */
            MPTS,

            /**
             * Contained in a container format, such as MKV, MP4, etc.
             */
            CONTAINED,
        }
    }
}
