package com.wynime.datasources.api.matcher

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaSource

fun interface WebVideoMatcher {
    sealed class MatchResult {
        data class Matched(
            val video: WebVideo
        ) : MatchResult()

        data object Continue : MatchResult()
        data object LoadPage : MatchResult()
    }

    fun match(
        url: String,
        context: WebVideoMatcherContext
    ): MatchResult

    fun patchConfig(config: WebViewConfig): WebViewConfig = config
}

data class WebViewConfig(
    val cookies: List<String> = emptyList(),
) {
    companion object {
        val Empty = WebViewConfig()
    }
}

val WebVideoMatcher.MatchResult.videoOrNull get() = (this as? WebVideoMatcher.MatchResult.Matched)?.video

class WebVideoMatcherContext(
    val media: Media,

) {

}

interface WebVideoMatcherProvider {
    val matcher: WebVideoMatcher
}

class MediaSourceWebVideoMatcherLoader(
    private val mediaSources: Flow<List<MediaSource>>
) {
    suspend fun loadMatchers(mediaSourceId: String): List<WebVideoMatcher> {
        return mediaSources.first().asSequence()
            .filter { it.mediaSourceId == mediaSourceId }
            .filterIsInstance<WebVideoMatcherProvider>()
            .map { it.matcher }
            .toList()
    }
}

class WebVideoRequestInfo(
    val url: String,
    val headers: Map<String, String>
)

data class WebVideo(

    val m3u8Url: String,

    val headers: Map<String, String>
)
