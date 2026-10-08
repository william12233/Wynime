package tw.wynime.sources

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import com.wynime.source.plugin.api.ResolvedMedia
import com.wynime.source.plugin.api.ResolvedMediaFormat
import com.wynime.source.plugin.api.SourceHttpClient
import com.wynime.source.plugin.api.SourceHttpRequest
import com.wynime.source.plugin.api.SourceHttpResponse
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginEntryPoint
import com.wynime.source.plugin.api.SourcePluginLogger
import com.wynime.source.plugin.api.SourcePluginPlatform
import com.wynime.source.plugin.api.SourceResolveRequest
import com.wynime.source.plugin.api.SourceSearchRequest
import com.wynime.source.plugin.api.SourceSubject
import tw.wynime.sources.dida.DidaEntryPoint
import tw.wynime.sources.dm1.Dm1EntryPoint
import tw.wynime.sources.dmbus.DmbusEntryPoint
import tw.wynime.sources.dyttzy.DyttzyEntryPoint
import tw.wynime.sources.eacg.EacgEntryPoint
import tw.wynime.sources.girigiri.GirigiriEntryPoint
import tw.wynime.sources.next.NextEntryPoint
import tw.wynime.sources.rk2.Rk2EntryPoint

class SourcePluginLiveSmokeTest {
    @Test
    fun `published source plugins produce a classified live report`() = runBlocking {
        val report = SourcePluginLiveSmokeReport(LiveHttpClient(), liveSmokeSpecs()).run()

        println(report.render())
    }
}

fun main() = runBlocking {
    println(SourcePluginLiveSmokeReport(LiveHttpClient(), liveSmokeSpecs()).run().render())
}

private fun liveSmokeSpecs(): List<SmokeSpec> {
    val queryOverride = System.getenv("WYNIME_SOURCE_PLUGIN_LIVE_QUERY")?.trim()
        ?.takeIf(String::isNotBlank)
    if (queryOverride == null) return defaultLiveSmokeSpecs()

    val episodeOverride = System.getenv("WYNIME_SOURCE_PLUGIN_LIVE_EPISODE")
        ?.trim()
        ?.toIntOrNull()
    return defaultLiveSmokeSpecs()
        .distinctBy { it.id }
        .map { spec ->
            spec.copy(
                query = queryOverride,
                episodeNumber = episodeOverride,
                verifyHlsPlaylist = false,
            )
        }
}

private fun defaultLiveSmokeSpecs() = listOf(
    SmokeSpec("2RK", "2rk", "關於鄰家的天使大人不知不覺把我慣成了廢人這檔子事", 3, Rk2EntryPoint()),
    SmokeSpec("DIDA", "dida", "咒術迴戰", null, DidaEntryPoint()),
    SmokeSpec("DM1", "dm1", "咒術迴戰", null, Dm1EntryPoint()),
    SmokeSpec("DMBUS", "dmbus", "咒術迴戰", null, DmbusEntryPoint()),
    SmokeSpec("E-ACG", "eacg", "咒術迴戰", null, EacgEntryPoint()),
    SmokeSpec("Girigiri", "girigiri", "咒術迴戰", null, GirigiriEntryPoint()),
    SmokeSpec("Next", "next", "咒術迴戰", null, NextEntryPoint()),
    SmokeSpec("電影天堂", "dyttzy", "哪吒之魔童闹海", null, DyttzyEntryPoint(), verifyHlsPlaylist = true),
    SmokeSpec(
        "Next current target",
        "next",
        "遭到流放的转生重骑士凭借游戏知识大开无双",
        14,
        NextEntryPoint(),
    ),
)

private data class SmokeSpec(
    val label: String,
    val id: String,
    val query: String,
    val episodeNumber: Int?,
    val entryPoint: SourcePluginEntryPoint,
    val verifyHlsPlaylist: Boolean = false,
)

private class SourcePluginLiveSmokeReport(
    private val httpClient: LiveHttpClient,
    private val specs: List<SmokeSpec>,
) {
    suspend fun run(): List<SmokeResult> = specs.map { runOne(it) }

    private suspend fun runOne(spec: SmokeSpec): SmokeResult {
        val plugin = try {
            spec.entryPoint.create(LiveContext(spec.id, httpClient))
        } catch (error: Throwable) {
            return SmokeResult.failure(spec, LiveStatus.PARSE_ERROR, "create: ${error.shortMessage()}")
        }

        try {
            val search = try {
                plugin.search(
                    SourceSearchRequest(
                        query = spec.query,
                        limit = 20,
                        traceId = "live-${spec.id}-search",
                        entryPoint = "SEARCH_REQUEST",
                    ),
                )
            } catch (error: Throwable) {
                return SmokeResult.failure(spec, classify(error, LiveStatus.PARSE_ERROR), "search: ${error.shortMessage()}")
            }
            if (search.isEmpty()) {
                return SmokeResult.terminal(spec, LiveStatus.SUBJECT_NO_MATCH, "search returned 0 results")
            }

            val subject = selectSubject(search, spec)
            val details = try {
                plugin.getSubject(subject.id)
            } catch (error: Throwable) {
                return SmokeResult.failure(spec, classify(error, LiveStatus.PARSE_ERROR), "subject: ${error.shortMessage()}")
            }
            if (details.channels.isEmpty()) {
                return SmokeResult.terminal(spec, LiveStatus.EPISODE_NO_MATCH, "subject has no channels")
            }

            val episodes = details.channels.flatMap { channel ->
                channel.episodes.map { episode -> channel.channel to episode }
            }
            val selected = if (spec.episodeNumber == null) {
                episodes.firstOrNull()
            } else {
                episodes.firstOrNull { (_, episode) ->
                    episode.episodeSort?.toInt() == spec.episodeNumber ||
                        Regex("(?<!\\d)0?${spec.episodeNumber}(?!\\d)").containsMatchIn(episode.displayName)
                }
            }
            if (selected == null) {
                return SmokeResult.terminal(spec, LiveStatus.EPISODE_NO_MATCH, "requested episode not present")
            }

            val (channel, episode) = selected
            val media = try {
                plugin.resolve(
                    SourceResolveRequest(
                        subjectId = subject.id,
                        channelId = channel.id,
                        episodeId = episode.id,
                        episodeSort = episode.episodeSort,
                        episodeEp = episode.episodeEp,
                        pluginId = spec.id,
                        traceId = "live-${spec.id}-resolve",
                        entryPoint = "PLAY_RESOLVE",
                    ),
                )
            } catch (error: Throwable) {
                return SmokeResult.failure(spec, classify(error, LiveStatus.RESOLVE_ERROR), "resolve: ${error.shortMessage()}")
            }
            if (spec.verifyHlsPlaylist && media.format != ResolvedMediaFormat.HLS) {
                return SmokeResult.failure(spec, LiveStatus.PLAYBACK_ERROR, "media format is ${media.format}, expected HLS")
            }
            val probe = try {
                httpClient.probe(media, verifyHlsPlaylist = spec.verifyHlsPlaylist)
            } catch (error: Throwable) {
                return SmokeResult.failure(spec, classify(error, LiveStatus.PLAYBACK_ERROR), "media: ${error.shortMessage()}")
            }
            val finalStatus = if (probe.statusCode in 200..399) LiveStatus.PASS else LiveStatus.PLAYBACK_ERROR
            return SmokeResult(
                spec = spec,
                search = LiveStatus.PASS,
                subject = LiveStatus.PASS,
                episodes = LiveStatus.PASS,
                resolve = LiveStatus.PASS,
                finalMedia = finalStatus,
                playback = LiveStatus.UNVERIFIED,
                download = LiveStatus.UNVERIFIED,
                detail = "${subject.title} / ${episode.displayName} / ${media.url.safeUrl()}",
            )
        } finally {
            plugin.close()
        }
    }

    private fun selectSubject(results: List<SourceSubject>, spec: SmokeSpec): SourceSubject =
        results.maxByOrNull { subject ->
            var score = 0
            if (subject.title.contains(spec.query, ignoreCase = true)) score += 10
            if (spec.id == "2rk" && subject.title.contains("天使大人")) score += 20
            score
        } ?: results.first()

    private fun classify(error: Throwable, fallback: LiveStatus): LiveStatus = when (error) {
        is LiveChallengeException -> LiveStatus.CHALLENGE
        is LiveHttpException -> LiveStatus.HTTP_ERROR
        is java.net.http.HttpTimeoutException -> LiveStatus.HTTP_ERROR
        is java.io.IOException -> LiveStatus.HTTP_ERROR
        is NoSuchMethodError, is NoClassDefFoundError, is AbstractMethodError, is ClassCastException ->
            LiveStatus.PARSE_ERROR
        else -> fallback
    }
}

private data class SmokeResult(
    val spec: SmokeSpec,
    val search: LiveStatus,
    val subject: LiveStatus,
    val episodes: LiveStatus,
    val resolve: LiveStatus,
    val finalMedia: LiveStatus,
    val playback: LiveStatus,
    val download: LiveStatus,
    val detail: String,
) {
    fun renderRow(): String = listOf(
        spec.label,
        search,
        subject,
        episodes,
        resolve,
        finalMedia,
        playback,
        download,
        detail,
    ).joinToString(" | ")

    companion object {
        fun failure(spec: SmokeSpec, status: LiveStatus, detail: String): SmokeResult =
            terminal(spec, status, detail)

        fun terminal(spec: SmokeSpec, status: LiveStatus, detail: String): SmokeResult = SmokeResult(
            spec = spec,
            search = status,
            subject = status,
            episodes = status,
            resolve = status,
            finalMedia = status,
            playback = status,
            download = status,
            detail = detail,
        )
    }
}

private enum class LiveStatus {
    PASS,
    SUBJECT_NO_MATCH,
    EPISODE_NO_MATCH,
    CHALLENGE,
    HTTP_ERROR,
    PARSE_ERROR,
    RESOLVE_ERROR,
    PLAYBACK_ERROR,
    UNVERIFIED,
}

private class LiveContext(
    override val pluginId: String,
    httpClient: LiveHttpClient,
) : SourcePluginContext {
    override val hostVersion: String = "0.1.3"
    override val platform: SourcePluginPlatform = SourcePluginPlatform.DESKTOP
    override val http: SourceHttpClient = httpClient
    override val logger: SourcePluginLogger = LiveLogger
}

private object LiveLogger : SourcePluginLogger {
    override fun debug(message: String) = Unit
    override fun info(message: String) = Unit
    override fun warn(message: String, throwable: Throwable?) = Unit
    override fun error(message: String, throwable: Throwable?) = Unit
}

private class LiveHttpClient : SourceHttpClient {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    override suspend fun execute(request: SourceHttpRequest): SourceHttpResponse {
        val uri = try {
            URI(request.url)
        } catch (error: IllegalArgumentException) {
            throw LiveHttpException("invalid URL", null, error)
        }
        val builder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(35))
        request.headers.forEach { (name, value) ->
            if (!name.equals("Host", ignoreCase = true) && !name.equals("Content-Length", ignoreCase = true)) {
                builder.header(name, value)
            }
        }
        val body = request.body?.let(HttpRequest.BodyPublishers::ofByteArray)
            ?: HttpRequest.BodyPublishers.noBody()
        val httpRequest = when (request.method.uppercase()) {
            "GET" -> builder.GET().build()
            "POST" -> builder.POST(body).build()
            "HEAD" -> builder.method("HEAD", body).build()
            else -> builder.method(request.method, body).build()
        }
        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
        val bodyBytes = response.body()
        val bodyText = bodyBytes.decodeToString()
        if (isChallenge(response.statusCode(), bodyText)) {
            throw LiveChallengeException(
                "HTTP ${response.statusCode()} challenge: ${bodyText.safePreview()}",
            )
        }
        if (response.statusCode() !in 200..399) {
            throw LiveHttpException(
                "HTTP ${response.statusCode()}: ${bodyText.safePreview()}",
                response.statusCode(),
            )
        }
        return SourceHttpResponse(
            statusCode = response.statusCode(),
            finalUrl = response.uri().toString(),
            headers = response.headers().map().mapValues { it.value.joinToString(",") },
            body = bodyBytes,
            contentType = response.headers().firstValue("Content-Type").orElse(null),
        )
    }

    fun probe(media: ResolvedMedia, verifyHlsPlaylist: Boolean): SourceHttpResponse = runBlocking {
        val response = execute(
            SourceHttpRequest(
                method = "GET",
                url = media.url,
                headers = media.requestHeaders() + ("Range" to "bytes=0-4095"),
                traceId = "live-media-probe",
                entryPoint = "FINAL_MEDIA_CHECK",
            ),
        )
        if (verifyHlsPlaylist && !response.bodyAsText().contains("#EXTM3U", ignoreCase = false)) {
            throw LiveHttpException("HLS response does not contain #EXTM3U", response.statusCode)
        }
        response
    }

    private fun isChallenge(statusCode: Int, body: String): Boolean {
        if (statusCode in setOf(403, 429, 503)) return true
        val lower = body.lowercase()
        return listOf("cloudflare", "cf-chl-", "turnstile", "captcha", "just a moment").any(lower::contains)
    }
}

private class LiveHttpException(message: String, val statusCode: Int?, cause: Throwable? = null) : IOException(message, cause)

private class LiveChallengeException(message: String) : IOException(message)

private fun List<SmokeResult>.render(): String = buildString {
    appendLine("Source plugin live smoke (non-blocking)")
    appendLine("Provider | Search | Subject | Episodes | Resolve | Final media | Playback | Download | Detail")
    this@render.forEach { appendLine(it.renderRow()) }
}

private fun String.safeUrl(): String = try {
    URI(this).let { uri -> URI(uri.scheme, uri.userInfo, uri.host, uri.port, uri.path, null, null).toString() }
} catch (_: Throwable) {
    "<invalid-url>"
}

private fun Throwable.shortMessage(): String = message?.take(160) ?: this::class.simpleName.orEmpty()

private fun String.safePreview(): String = replace(Regex("\\s+"), " ").trim().take(180)
