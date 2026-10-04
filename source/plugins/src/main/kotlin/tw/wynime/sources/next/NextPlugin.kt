package tw.wynime.sources.next

import me.him188.ani.source.plugin.api.ResolvedMediaFormat
import me.him188.ani.source.plugin.api.SourceChannel
import me.him188.ani.source.plugin.api.SourceChannelEpisodes
import me.him188.ani.source.plugin.api.SourcePluginContext
import me.him188.ani.source.plugin.api.SourcePluginEntryPoint
import me.him188.ani.source.plugin.api.SourcePlugin
import me.him188.ani.source.plugin.api.SourceResolveRequest
import me.him188.ani.source.plugin.api.SourceSearchRequest
import me.him188.ani.source.plugin.api.SourceSubjectDetails
import me.him188.ani.source.plugin.api.SourceWebResourceMatch
import tw.wynime.sources.shared.SitePluginBase
import tw.wynime.sources.shared.extractJsonNumberField
import tw.wynime.sources.shared.extractJsonStringField
import tw.wynime.sources.shared.isMediaUrl
import tw.wynime.sources.shared.jsonObjects
import tw.wynime.sources.shared.links
import tw.wynime.sources.shared.parseEpisodeNumber
import tw.wynime.sources.shared.searchQueryVariants
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class NextEntryPoint : SourcePluginEntryPoint {
    override fun create(context: SourcePluginContext): SourcePlugin = NextPlugin(context)
}

private const val PUBLIC_API_ROOT = "https://api.xifanacg.com"
private const val PUBLIC_API_KEY = "sb_publishable_OBIVAWACIX6lPXrO98_z24_HcsmalkA"

internal class NextPlugin(context: SourcePluginContext) : SitePluginBase(
    context = context,
    id = "next",
    displayName = "稀飯動漫 Next",
    rootUrl = "https://next.xifanacg.com",
    iconUrl = "https://next.xifanacg.com/favicon.ico?favicon.046zlab6jl7gk.ico",
    description = "稀飯動漫 Next 的 SSR 番劇與正常播放服務",
) {
    override suspend fun search(request: SourceSearchRequest): List<me.him188.ani.source.plugin.api.SourceSubject> {
        val page = requestPage("$rootUrl/search?q=${urlEncode(request.query)}")
        val variants = searchQueryVariants(request.query)
        val serverResults = mutableListOf<me.him188.ani.source.plugin.api.SourceSubject>()
        for (variant in variants) {
            serverResults += dynamicSearchLinks(
                page.html,
                variant,
                Regex("(?i)/anime/(\\d+)(?:[/?#]|$)"),
            )
        }
        val apiConfig = try {
            discoverPlaybackConfig(page.html)
        } catch (_: Throwable) {
            null
        } ?: (PUBLIC_API_ROOT to PUBLIC_API_KEY)
        val apiResults = mutableListOf<me.him188.ani.source.plugin.api.SourceSubject>()
        for (variant in variants) {
            apiResults += searchApi(apiConfig.first, apiConfig.second, page.finalUrl, variant, request.limit)
        }
        return (serverResults + apiResults)
            .distinctBy { it.id }
            .take(request.limit)
    }

    override suspend fun getSubject(subjectId: String): SourceSubjectDetails {
        val page = requestPage("$rootUrl/anime/$subjectId")
        val subjectTitle = Regex("(?is)<h1[^>]*>(.*?)</h1>")
            .find(page.html)?.groupValues?.getOrNull(1)?.let(::twClean).orEmpty()
            .ifBlank { page.title.substringBefore("（").substringBefore("(") }
            .ifBlank { subjectId }
        val subject = subject(subjectId, subjectTitle, page.finalUrl)
        val sourceIds = sourceIdsByCode(page.html)
        val groups = linkedMapOf<String, MutableList<me.him188.ani.source.plugin.api.SourceEpisode>>()
        links(page.html).forEach { link ->
            val match = Regex("(?i)/anime/${Regex.escape(subjectId)}/play/(\\d+)(?:\\?source=([^&#\"']+))?").find(link.href)
                ?: return@forEach
            val episodeId = match.groupValues[1]
            val sourceCode = match.groupValues.getOrNull(2).orEmpty().ifBlank {
                sourceIds.keys.firstOrNull().orEmpty().ifBlank { "default" }
            }
            groups.getOrPut(sourceCode) { mutableListOf() } += episode(
                id = episodeId,
                title = link.text.ifBlank { "第${episodeId}集" },
                pageUrl = absoluteUrl(rootUrl, link.href),
                episodeSort = episodeNumberById(page.html, episodeId)
                    ?: parseEpisodeNumber(link.text)
                    ?: episodeId.toFloatOrNull(),
            )
        }
        return SourceSubjectDetails(
            subject = subject,
            channels = groups.map { (code, episodes) ->
                SourceChannelEpisodes(
                    SourceChannel(code, sourceNameByCode(page.html, code) ?: "线路$code"),
                    episodes.distinctBy { it.id },
                )
            },
        )
    }

    override suspend fun resolve(request: SourceResolveRequest) = run {
        val pageUrl = "$rootUrl/anime/${request.subjectId}/play/${request.episodeId}?source=${urlEncode(request.channelId)}"
        val page = requestPage(pageUrl)
        val sourceId = sourceIdsByCode(page.html)[request.channelId]
            ?: request.channelId.toIntOrNull()
        val apiConfig = try {
            discoverPlaybackConfig(page.html)
        } catch (_: Throwable) {
            null
        } ?: (PUBLIC_API_ROOT to PUBLIC_API_KEY)
        val apiResult = if (sourceId != null) {
            val body = "{\"action\":\"fallback\",\"episode_id\":${request.episodeId.toInt()},\"source_id\":$sourceId}"
            var result: String? = null
            for (key in listOf(apiConfig.second, PUBLIC_API_KEY).distinct()) {
                result = try {
                    requestJson(
                        url = "${apiConfig.first}/functions/v1/issue-web-playback",
                        body = body,
                        headers = mapOf(
                            "apikey" to key,
                            "Authorization" to "Bearer $key",
                            "Origin" to rootUrl,
                            "Referer" to page.finalUrl,
                        ),
                    )
                } catch (_: Throwable) {
                    null
                }
                if (result != null) break
            }
            result
        } else {
            null
        }
        val mediaUrl = apiResult?.let {
            extractJsonStringField(it, "master_playlist") ?: extractJsonStringField(it, "url")
        }
        if (mediaUrl != null && isMediaUrl(mediaUrl)) {
            resolvedMedia(request, page.finalUrl, mediaUrl)
        } else {
            resolvedMedia(request, page.finalUrl, page.finalUrl)
        }
    }

    override fun matchWebResource(url: String): SourceWebResourceMatch = when {
        isMediaUrl(url) -> SourceWebResourceMatch.Matched(url)
        else -> SourceWebResourceMatch.Continue
    }

    private fun sourceIdsByCode(html: String): Map<String, Int> = Regex(
        "(?is)(?:\\\\\"|\")id(?:\\\\\"|\")\\s*:\\s*(\\d+)\\s*,\\s*(?:\\\\\"|\")code(?:\\\\\"|\")\\s*:\\s*(?:\\\\\"|\")([^\"\\\\]+)",
    ).findAll(html).associate { it.groupValues[2] to it.groupValues[1].toInt() }

    private fun sourceNameByCode(html: String, code: String): String? = Regex(
        "(?is)(?:\\\\\"|\")code(?:\\\\\"|\")\\s*:\\s*(?:\\\\\"|\")${Regex.escape(code)}(?:\\\\\"|\").{0,180}?(?:\\\\\"|\")name(?:\\\\\"|\")\\s*:\\s*(?:\\\\\"|\")([^\"\\\\]+)",
    ).find(html)?.groupValues?.getOrNull(1)

    private fun episodeNumberById(html: String, episodeId: String): Float? = Regex(
        """(?is)(?:\\"id\\"|"id")\s*:\s*${Regex.escape(episodeId)}\s*,.*?(?:\\"episode_number\\"|"episode_number")\s*:\s*([0-9]+(?:\.[0-9]+)?)""",
    ).find(html)?.groupValues?.getOrNull(1)?.toFloatOrNull()

    private suspend fun discoverPlaybackConfig(html: String): Pair<String, String>? {
        val chunkPaths = Regex("/_next/static/chunks/[^\"\\\\]+\\.js")
            .findAll(html)
            .map { it.value }
            .distinct()
        val chunks = fetchChunksConcurrently(chunkPaths.toList())
        var discoveredApi: String? = null
        for ((chunkPath, chunk) in chunks) {
            discoveredApi = discoveredApi ?: Regex("https://[A-Za-z0-9.-]+(?:/functions/v1)?")
                .find(chunk)
                ?.value
                ?.removeSuffix("/functions/v1")
            val keyMatch = Regex("sb_publishable_[A-Za-z0-9_-]+").find(chunk) ?: continue
            val contextStart = maxOf(0, keyMatch.range.first - 600)
            val contextEnd = minOf(chunk.length, keyMatch.range.last + 600)
            val configContext = chunk.substring(contextStart, contextEnd)
            val api = Regex("https://[A-Za-z0-9.-]+(?:/functions/v1)?")
                .findAll(configContext)
                .map { match ->
                    val absoluteStart = contextStart + match.range.first
                    absoluteStart to match.value.removeSuffix("/functions/v1")
                }
                .minByOrNull { (absoluteStart, _) -> kotlin.math.abs(absoluteStart - keyMatch.range.first) }
                ?.second
                ?: continue
            return api to keyMatch.value
        }
        return (discoveredApi ?: PUBLIC_API_ROOT) to PUBLIC_API_KEY
    }

    private suspend fun searchApi(
        api: String,
        discoveredKey: String,
        referer: String,
        query: String,
        limit: Int,
    ): List<me.him188.ani.source.plugin.api.SourceSubject> {
        val body = "{\"search_term\":${jsonString(query)},\"page_number\":1,\"items_per_page\":$limit,\"sort_by\":\"created_at\",\"sort_order\":\"desc\"}"
        val keys = listOf(discoveredKey, PUBLIC_API_KEY).distinct()
        for ((index, key) in keys.withIndex()) {
            val response = try {
                requestJson(
                    url = "$api/rest/v1/rpc/search_animes",
                    body = body,
                    headers = mapOf(
                        "apikey" to key,
                        "Authorization" to "Bearer $key",
                        "Origin" to rootUrl,
                        "Referer" to referer,
                    ),
                )
            } catch (_: Throwable) {
                continue
            }
            val parsed = parseSearchResults(response)
            if (parsed.isNotEmpty() || index == keys.lastIndex) return parsed
        }
        return emptyList()
    }

    private fun fetchChunksConcurrently(paths: List<String>): List<Pair<String, String>> {
        if (paths.isEmpty()) return emptyList()
        val executor = Executors.newFixedThreadPool(minOf(paths.size, 16))
        return try {
            val tasks = paths.map { path ->
                Callable {
                    runCatching {
                        path to runBlockingRequest { requestPage(absoluteUrl(rootUrl, path)).html }
                    }.getOrNull()
                }
            }
            executor.invokeAll(tasks, 10, TimeUnit.SECONDS)
                .mapNotNull { future ->
                    if (future.isCancelled) null else runCatching { future.get() }.getOrNull()
                }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun <T> runBlockingRequest(block: suspend () -> T): T {
        var result: Result<T>? = null
        val completed = CountDownLatch(1)
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(value: Result<T>) {
                result = value
                completed.countDown()
            }
        })
        if (!completed.await(12, TimeUnit.SECONDS)) {
            throw TimeoutException("Timed out while discovering Next playback configuration")
        }
        return result?.getOrThrow() ?: error("Next playback configuration request did not complete")
    }

    private fun parseSearchResults(response: String): List<me.him188.ani.source.plugin.api.SourceSubject> =
        jsonObjects(response).mapNotNull { item ->
            val id = extractJsonNumberField(item, "id")?.toString() ?: return@mapNotNull null
            val title = extractJsonStringField(item, "title")
                ?: extractJsonStringField(item, "title_original")
                ?: return@mapNotNull null
            subject(id, title, "$rootUrl/anime/$id", extractJsonStringField(item, "cover_url"))
        }.distinctBy { it.id }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

    private fun twClean(value: String): String = tw.wynime.sources.shared.cleanText(value)

}
