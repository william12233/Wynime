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
    ) : ResourceLocation() {
        init {
            require(uri.startsWith("https://") || uri.startsWith("http://")) {
                "WebVideo uri must start with 'http://' or 'https://', but was $uri"
            }
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
