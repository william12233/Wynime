package tw.wynime.sources.dyttzy

import com.wynime.source.plugin.api.ResolvedMedia
import com.wynime.source.plugin.api.ResolvedMediaFormat
import com.wynime.source.plugin.api.SourceChannel
import com.wynime.source.plugin.api.SourceChannelEpisodes
import com.wynime.source.plugin.api.SourcePlugin
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginEntryPoint
import com.wynime.source.plugin.api.SourceResolveRequest
import com.wynime.source.plugin.api.SourceSearchRequest
import com.wynime.source.plugin.api.SourceSubject
import com.wynime.source.plugin.api.SourceSubjectDetails
import com.wynime.source.plugin.api.SourceEpisode
import com.wynime.source.plugin.api.SourceWebResourceMatch
import com.wynime.source.plugin.api.mediaIdentity
import java.net.URI
import tw.wynime.sources.shared.SitePluginBase
import tw.wynime.sources.shared.SourceSiteException
import tw.wynime.sources.shared.cleanText
import tw.wynime.sources.shared.extractJsonNumberField
import tw.wynime.sources.shared.extractJsonStringField
import tw.wynime.sources.shared.isHttpUrl
import tw.wynime.sources.shared.isMediaUrl
import tw.wynime.sources.shared.jsonArrayObjects
import tw.wynime.sources.shared.mediaFormat
import tw.wynime.sources.shared.parseEpisodeNumber

class DyttzyEntryPoint : SourcePluginEntryPoint {
    override fun create(context: SourcePluginContext): SourcePlugin = DyttzyPlugin(context)
}

internal class DyttzyPlugin(context: SourcePluginContext) : SitePluginBase(
    context = context,
    id = "dyttzy",
    displayName = "電影天堂",
    rootUrl = ROOT_URL,
    iconUrl = "$ROOT_URL/favicon.ico",
    description = "電影天堂公開 API 的直接 HLS 來源",
) {
    override suspend fun search(request: SourceSearchRequest): List<SourceSubject> {
        val page = requestPage(
            searchUrl(request.query),
            headers = JSON_HEADERS,
            traceId = request.traceId,
            entryPoint = request.entryPoint,
        )
        return jsonArrayObjects(page.html, "list")
            .mapNotNull(::parseSubject)
            .distinctBy { it.id }
            .take(request.limit.coerceAtLeast(0))
    }

    override suspend fun getSubject(subjectId: String): SourceSubjectDetails {
        val detailUrl = detailUrl(subjectId)
        val parsed = parseDetail(
            item = requestDetail(subjectId, traceId = "", entryPoint = "SUBJECT_RESOLVE"),
            subjectId = subjectId,
            detailUrl = detailUrl,
        )
        return parsed.toDetails()
    }

    override suspend fun resolve(request: SourceResolveRequest): ResolvedMedia {
        val detailUrl = detailUrl(request.subjectId)
        val parsed = parseDetail(
            item = requestDetail(
                subjectId = request.subjectId,
                traceId = request.traceId,
                entryPoint = request.entryPoint,
            ),
            subjectId = request.subjectId,
            detailUrl = detailUrl,
        )
        val episode = parsed.channels
            .firstOrNull { it.channel.id == request.channelId }
            ?.episodes
            ?.firstOrNull { it.episode.id == request.episodeId }
            ?: throw SourceSiteException(
                "電影天堂找不到可播放的集數: ${request.subjectId}/${request.channelId}/${request.episodeId}",
            )

        return ResolvedMedia(
            stableIdentity = request.mediaIdentity(metadata.id).asStableId(),
            url = episode.mediaUrl,
            format = ResolvedMediaFormat.HLS,
            headers = MEDIA_HEADERS,
            originalPageUrl = detailUrl,
        )
    }

    override fun matchWebResource(url: String): SourceWebResourceMatch = when {
        isMediaUrl(url) -> SourceWebResourceMatch.Matched(url)
        else -> SourceWebResourceMatch.Continue
    }

    private suspend fun requestDetail(
        subjectId: String,
        traceId: String,
        entryPoint: String,
    ): String {
        val page = requestPage(
            detailUrl(subjectId),
            headers = JSON_HEADERS,
            traceId = traceId,
            entryPoint = entryPoint,
        )
        return jsonArrayObjects(page.html, "list").firstOrNull()
            ?: throw SourceSiteException("電影天堂沒有回傳 subject 詳情: $subjectId")
    }

    private fun parseSubject(item: String): SourceSubject? {
        val id = extractJsonNumberField(item, "vod_id")?.toString()
            ?: extractJsonStringField(item, "vod_id")?.trim()?.takeIf(String::isNotBlank)
            ?: return null
        val title = extractJsonStringField(item, "vod_name")
            ?.let(::cleanText)
            ?.takeIf(String::isNotBlank)
            ?: return null
        return subject(id, title, detailUrl(id), coverUrl(item))
    }

    private fun parseDetail(item: String, subjectId: String, detailUrl: String): ParsedDetail {
        val title = extractJsonStringField(item, "vod_name")
            ?.let(::cleanText)
            ?.takeIf(String::isNotBlank)
            ?: subjectId
        val subject = subject(
            id = subjectId,
            title = title,
            detailUrl = detailUrl,
            coverUrl = coverUrl(item),
        )
        val sourceNames = extractJsonStringField(item, "vod_play_from")
            .orEmpty()
            .split(SOURCE_SEPARATOR)
        val sourceLists = extractJsonStringField(item, "vod_play_url")
            .orEmpty()
            .split(SOURCE_SEPARATOR)
        val episodes = sourceNames.mapIndexedNotNull { sourceIndex, sourceName ->
            if (!sourceName.trim().equals(CHANNEL_ID, ignoreCase = true)) return@mapIndexedNotNull null
            parseEpisodes(sourceLists.getOrNull(sourceIndex).orEmpty(), detailUrl)
        }.flatten().distinctBy { it.mediaUrl }

        return ParsedDetail(
            subject = subject,
            channels = if (episodes.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    ParsedChannel(
                        channel = SourceChannel(CHANNEL_ID, CHANNEL_ID),
                        episodes = episodes.mapIndexed { index, parsedEpisode ->
                            val episodeSort = parsedEpisode.episode.episodeSort
                                ?: (index + 1).toFloat()
                            parsedEpisode.copy(
                                episode = episode(
                                    id = "$CHANNEL_ID-${index + 1}",
                                    title = parsedEpisode.episode.displayName,
                                    pageUrl = detailUrl,
                                    episodeSort = episodeSort,
                                ),
                            )
                        },
                    ),
                )
            },
        )
    }

    private fun parseEpisodes(rawSource: String, detailUrl: String): List<ParsedEpisode> = rawSource
        .split(EPISODE_SEPARATOR)
        .mapNotNull { rawEpisode ->
            val separator = rawEpisode.indexOf(EPISODE_VALUE_SEPARATOR)
            if (separator <= 0 || separator == rawEpisode.lastIndex) return@mapNotNull null
            val title = cleanText(rawEpisode.substring(0, separator))
                .ifBlank { return@mapNotNull null }
            val mediaUrl = rawEpisode.substring(separator + 1).trim()
            if (!isDirectHttpsHls(mediaUrl)) return@mapNotNull null
            ParsedEpisode(
                episode = episode(
                    id = "pending",
                    title = title,
                    pageUrl = detailUrl,
                    episodeSort = parseEpisodeNumber(title),
                ),
                mediaUrl = mediaUrl,
            )
        }

    private fun coverUrl(item: String): String? = extractJsonStringField(item, "vod_pic")
        ?.trim()
        ?.takeIf(::isHttpUrl)

    private fun isDirectHttpsHls(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            isMediaUrl(url) &&
            mediaFormat(url) == ResolvedMediaFormat.HLS
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun searchUrl(query: String): String =
        "$API_URL?ac=videolist&wd=${urlEncode(query)}"

    private fun detailUrl(subjectId: String): String =
        "$API_URL?ac=videolist&ids=${urlEncode(subjectId)}"

    private data class ParsedDetail(
        val subject: SourceSubject,
        val channels: List<ParsedChannel>,
    ) {
        fun toDetails(): SourceSubjectDetails = SourceSubjectDetails(
            subject = subject,
            channels = channels.map { parsedChannel ->
                SourceChannelEpisodes(
                    channel = parsedChannel.channel,
                    episodes = parsedChannel.episodes.map { it.episode },
                )
            },
        )
    }

    private data class ParsedChannel(
        val channel: SourceChannel,
        val episodes: List<ParsedEpisode>,
    )

    private data class ParsedEpisode(
        val episode: SourceEpisode,
        val mediaUrl: String,
    )

    private companion object {
        const val ROOT_URL = "https://caiji.dyttzyapi.com"
        const val API_URL = "$ROOT_URL/api.php/provide/vod"
        const val CHANNEL_ID = "dyttm3u8"
        const val SOURCE_SEPARATOR = "\$\$\$"
        const val EPISODE_SEPARATOR = "#"
        const val EPISODE_VALUE_SEPARATOR = '$'
        val JSON_HEADERS = mapOf("Accept" to "application/json")
        val MEDIA_HEADERS = mapOf(
            "Accept" to "application/vnd.apple.mpegurl,application/x-mpegURL,*/*",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Safari/537.36",
        )
    }
}
