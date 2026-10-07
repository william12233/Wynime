package com.wynime.datasources.api.topic

import kotlinx.serialization.SerialName

import kotlinx.serialization.Serializable

@Serializable
sealed class ResourceLocation {
    abstract val uri: String

    @SerialName("me.him188.ani.datasources.api.topic.ResourceLocation.HttpStreamingFile")
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

    @SerialName("me.him188.ani.datasources.api.topic.ResourceLocation.WebVideo")
    @Serializable
    data class WebVideo(

        override val uri: String,

        val headers: Map<String, String> = emptyMap(),
    ) : ResourceLocation() {
        init {
            require(uri.startsWith("https://") || uri.startsWith("http://")) {
                "WebVideo uri must start with 'http://' or 'https://', but was $uri"
            }
        }
    }

    @SerialName("me.him188.ani.datasources.api.topic.ResourceLocation.SourcePluginMedia")
    @Serializable
    data class SourcePluginMedia(
        val pluginId: String,
        val subjectId: String,
        val channelId: String,
        val episodeId: String,

        override val uri: String,

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

    @SerialName("me.him188.ani.datasources.api.topic.ResourceLocation.LocalFile")
    @Serializable
    data class LocalFile(
        val filePath: String,

        val fileType: FileType? = null,

        val originalUri: String? = null,
    ) : ResourceLocation() {

        override val uri: String by lazy {
            "file://${filePath}"
        }

        @Serializable
        enum class FileType {

            MPTS,

            CONTAINED,
        }
    }
}
