/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.sourceplugin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.matcher.WebVideo
import me.him188.ani.datasources.api.matcher.WebVideoMatcher
import me.him188.ani.datasources.api.matcher.WebVideoMatcherContext
import me.him188.ani.datasources.api.matcher.WebVideoMatcherProvider
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.source.plugin.api.SourceConnectionState
import me.him188.ani.source.plugin.api.SourceDiagnostics
import me.him188.ani.source.plugin.api.SourceResultStatus
import me.him188.ani.source.plugin.api.SourcePlugin
import me.him188.ani.source.plugin.api.SourceSearchRequest
import me.him188.ani.source.plugin.api.SourceMediaIdentity
import me.him188.ani.source.plugin.api.SourceSubject
import me.him188.ani.source.plugin.api.SourceTracePhase
import me.him188.ani.source.plugin.api.SourceWebResourceMatch
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.Uuid

/** Adapts one executable plugin to the host media-source contract. */
class SourcePluginMediaSource(
    private val plugin: SourcePlugin,
) : MediaSource, WebVideoMatcherProvider {
    private val logger = logger<SourcePluginMediaSource>()

    override val mediaSourceId: String = plugin.metadata.id
    override val kind: MediaSourceKind = MediaSourceKind.WEB
    override val info: MediaSourceInfo = MediaSourceInfo(
        displayName = plugin.metadata.displayName,
        description = plugin.metadata.description,
        websiteUrl = plugin.metadata.website,
        iconUrl = plugin.metadata.iconUrl,
        tier = null,
    )

    override val matcher: WebVideoMatcher = WebVideoMatcher { url, _: WebVideoMatcherContext ->
        when (val match = plugin.matchWebResource(url)) {
            SourceWebResourceMatch.Continue -> WebVideoMatcher.MatchResult.Continue
            SourceWebResourceMatch.LoadPage -> WebVideoMatcher.MatchResult.LoadPage
            is SourceWebResourceMatch.Matched -> WebVideoMatcher.MatchResult.Matched(
                WebVideo(match.url, match.headers),
            )
        }
    }

    override suspend fun checkConnection(): ConnectionStatus = when (plugin.checkConnection().state) {
        SourceConnectionState.CONNECTED -> ConnectionStatus.SUCCESS
        SourceConnectionState.AUTH_REQUIRED,
        SourceConnectionState.BLOCKED,
        SourceConnectionState.FAILED,
        -> ConnectionStatus.FAILED
    }

    override suspend fun searchSubjects(keyword: String): List<BrowseSubject> {
        val traceId = Uuid.randomString()
        trace(traceId, SourceTracePhase.SEARCH_REQUEST, SourceResultStatus.SUCCESS, query = keyword)
        val subjects = try {
            plugin.search(SourceSearchRequest(keyword, traceId = traceId, entryPoint = "browse-search"))
        } catch (error: SourcePluginFailure) {
            traceFailure(traceId, SourceTracePhase.SEARCH_RESPONSE, error)
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw sourcePluginBoundaryFailure(
                traceId = traceId,
                provider = mediaSourceId,
                entryPoint = "browse-search",
                fallbackStatus = SourceResultStatus.PARSE_ERROR,
                error = error,
                url = keyword,
                retryable = false,
            )
        }
        trace(
            traceId,
            SourceTracePhase.SEARCH_RESPONSE,
            if (subjects.isEmpty()) SourceResultStatus.SUBJECT_NO_MATCH else SourceResultStatus.SUCCESS,
            query = keyword,
            parserResultCount = subjects.size,
        )
        return subjects.map { subject ->
            BrowseSubject(
                name = subject.title,
                url = subject.detailUrl?.withPluginSubjectMarker(subject.id)
                    ?: internalSubjectUrl(subject.id),
            )
        }
    }

    override suspend fun browseSubject(subject: BrowseSubject): List<BrowseChannel> {
        val details = plugin.getSubject(subjectIdFromBrowseSubject(subject))
        val subjectPageUrl = subject.url.withoutPluginMarker()
        return details.channels.map { channel ->
            BrowseChannel(
                name = channel.channel.displayName,
                label = channel.channel.displayName,
                episodes = channel.episodes.map { episode ->
                    BrowseEpisode(
                        name = episode.displayName,
                        url = (episode.playPageUrl ?: subjectPageUrl).withPluginEpisodeMarker(
                            subjectId = details.subject.id,
                            channelId = channel.channel.id,
                            episodeId = episode.id,
                        ),
                        episodeSort = episodeSort(episode.episodeSort, episode.episodeEp, episode.displayName),
                    )
                },
            )
        }
    }

    override fun createMedia(
        subject: BrowseSubject,
        channelName: String?,
        episode: BrowseEpisode,
        episodeSort: EpisodeSort?,
    ): DefaultMedia {
        val reference = episode.url.readPluginEpisodeMarker()
            ?: throw UnsupportedOperationException("Source plugin browse URL does not contain an episode reference")
        val pageUrl = episode.url.withoutPluginMarker()
        val identity = SourceMediaIdentity(
            pluginId = mediaSourceId,
            subjectId = reference.subjectId,
            channelId = reference.channelId,
            episodeId = reference.episodeId,
        )
        return DefaultMedia(
            mediaId = identity.asStableId(),
            mediaSourceId = mediaSourceId,
            originalUrl = pageUrl,
            download = ResourceLocation.SourcePluginMedia(
                pluginId = mediaSourceId,
                subjectId = reference.subjectId,
                channelId = reference.channelId,
                episodeId = reference.episodeId,
                uri = pageUrl,
            ),
            originalTitle = "${subject.name} ${channelName.orEmpty()} ${episode.name}".trim(),
            publishedTime = 0L,
            properties = MediaProperties(
                subjectName = subject.name,
                episodeName = episode.name,
                subtitleLanguageIds = plugin.metadata.defaultSubtitleLanguageIds,
                resolution = "",
                alliance = channelName.orEmpty().ifBlank { mediaSourceId },
                size = FileSize.Unspecified,
                subtitleKind = null,
            ),
            episodeRange = (episodeSort ?: episode.episodeSort)?.let(EpisodeRange::single),
            location = MediaSourceLocation.Online,
            kind = MediaSourceKind.WEB,
        )
    }

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> = SinglePagePagedSource {
        flow {
            val traceId = query.traceId.ifBlank { Uuid.randomString() }
            val names = query.subjectNames.ifEmpty { listOfNotNull(query.subjectNameCN) }
            trace(traceId, SourceTracePhase.DISCOVERY_START, SourceResultStatus.SUCCESS, query = names.joinToString(" | "))
            val subjects = buildList {
                val seen = HashSet<String>()
                for (name in names) {
                    trace(traceId, SourceTracePhase.SEARCH_REQUEST, SourceResultStatus.SUCCESS, query = name)
                    val searchResults = try {
                        plugin.search(SourceSearchRequest(name, traceId = traceId, entryPoint = "discovery-search"))
                    } catch (error: SourcePluginFailure) {
                        traceFailure(traceId, SourceTracePhase.SEARCH_RESPONSE, error)
                        throw error
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        throw sourcePluginBoundaryFailure(
                            traceId = traceId,
                            provider = mediaSourceId,
                            entryPoint = "discovery-search",
                            fallbackStatus = SourceResultStatus.PARSE_ERROR,
                            error = error,
                            url = name,
                            retryable = false,
                        )
                    }
                    trace(
                        traceId,
                        SourceTracePhase.SEARCH_RESPONSE,
                        if (searchResults.isEmpty()) SourceResultStatus.SUBJECT_NO_MATCH else SourceResultStatus.SUCCESS,
                        query = name,
                        parserResultCount = searchResults.size,
                    )
                    for (subject in searchResults) {
                        if (seen.add(subject.id)) add(subject)
                    }
                }
            }
            val selectedSubject = selectBestSourceSubject(subjects, names)
            if (selectedSubject == null) {
                val diagnostics = trace(
                    traceId,
                    SourceTracePhase.MATCH_RESULT,
                    SourceResultStatus.SUBJECT_NO_MATCH,
                    query = names.joinToString(" | "),
                    parserResultCount = subjects.size,
                    failureReason = "no safe subject match",
                )
                throw SourcePluginNoMatchException(diagnostics)
            }
            val subject = selectedSubject.subject
            trace(
                traceId,
                SourceTracePhase.MATCH_RESULT,
                SourceResultStatus.SUCCESS,
                query = names.joinToString(" | "),
                url = subject.detailUrl,
                parserResultCount = subjects.size,
                matcherScore = selectedSubject.score,
            )
            trace(
                traceId,
                SourceTracePhase.SUBJECT_RESOLVE,
                SourceResultStatus.SUCCESS,
                url = subject.detailUrl,
            )
            var matchCount = 0
            trace(traceId, SourceTracePhase.EPISODE_FETCH, SourceResultStatus.SUCCESS, url = subject.detailUrl)
            val details = try {
                plugin.getSubject(subject.id)
            } catch (error: SourcePluginFailure) {
                traceFailure(traceId, SourceTracePhase.EPISODE_FETCH, error)
                throw error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                    throw sourcePluginBoundaryFailure(
                        traceId = traceId,
                        provider = mediaSourceId,
                        entryPoint = "episode-fetch",
                        fallbackStatus = SourceResultStatus.PARSE_ERROR,
                        error = error,
                        url = subject.detailUrl,
                        retryable = false,
                    )
            }
            for (channel in details.channels) {
                for (episode in channel.episodes) {
                    val pageUrl = episode.playPageUrl ?: subject.detailUrl ?: continue
                    val sort = episodeSort(episode.episodeSort, episode.episodeEp, episode.displayName)
                    val identity = SourceMediaIdentity(
                        pluginId = mediaSourceId,
                        subjectId = subject.id,
                        channelId = channel.channel.id,
                        episodeId = episode.id,
                    )
                    matchCount++
                    emit(
                        MediaMatch(
                            media = DefaultMedia(
                                mediaId = identity.asStableId(),
                                mediaSourceId = mediaSourceId,
                                originalUrl = subject.detailUrl ?: pageUrl,
                                download = ResourceLocation.SourcePluginMedia(
                                    pluginId = mediaSourceId,
                                    subjectId = subject.id,
                                    channelId = channel.channel.id,
                                    episodeId = episode.id,
                                    uri = pageUrl,
                                    traceId = traceId,
                                ),
                                originalTitle = "${subject.title} ${channel.channel.displayName} ${episode.displayName}".trim(),
                                publishedTime = 0L,
                                properties = MediaProperties(
                                    subjectName = subject.title,
                                    episodeName = episode.displayName,
                                    subtitleLanguageIds = plugin.metadata.defaultSubtitleLanguageIds,
                                    resolution = "",
                                    alliance = channel.channel.displayName,
                                    size = FileSize.Unspecified,
                                    subtitleKind = null,
                                ),
                                episodeRange = sort?.let(EpisodeRange::single),
                                location = MediaSourceLocation.Online,
                                kind = MediaSourceKind.WEB,
                            ),
                            kind = if (selectedSubject.isExactTitle ||
                                (query.subjectId.isNotBlank() && subject.id == query.subjectId)
                            ) {
                                MatchKind.EXACT
                            } else {
                                MatchKind.FUZZY
                            },
                        ),
                    )
                }
            }
            if (matchCount == 0) {
                val diagnostics = trace(
                    traceId,
                    SourceTracePhase.EPISODE_MATCH,
                    SourceResultStatus.EPISODE_NO_MATCH,
                    url = subject.detailUrl,
                    parserResultCount = 0,
                    failureReason = "subject detail contained no playable episodes",
                )
                throw SourcePluginNoMatchException(diagnostics)
            }
            trace(
                traceId,
                SourceTracePhase.EPISODE_MATCH,
                SourceResultStatus.SUCCESS,
                url = subject.detailUrl,
                parserResultCount = matchCount,
            )
        }
    }

    private fun traceFailure(traceId: String, phase: SourceTracePhase, failure: SourcePluginFailure) {
        trace(
            traceId = traceId,
            phase = phase,
            status = failure.status,
            url = failure.diagnostics.url,
            statusCode = failure.diagnostics.statusCode,
            elapsedMillis = failure.diagnostics.elapsedMillis,
            failureReason = failure.diagnostics.failureReason,
        )
    }

    private fun trace(
        traceId: String,
        phase: SourceTracePhase,
        status: SourceResultStatus,
        query: String? = null,
        url: String? = null,
        statusCode: Int? = null,
        elapsedMillis: Long? = null,
        parserResultCount: Int? = null,
        matcherScore: Int? = null,
        failureReason: String? = null,
    ): SourceDiagnostics {
        val diagnostics = SourceDiagnostics(
            traceId = traceId,
            provider = mediaSourceId,
            entryPoint = phase.name,
            query = query,
            url = url?.let(::safeSourceUrl),
            domain = url?.let(::sourceDomain),
            statusCode = statusCode,
            elapsedMillis = elapsedMillis,
            responseCategory = status,
            userAgentProfile = "BROWSER",
            parserResultCount = parserResultCount,
            matcherScore = matcherScore,
            failureReason = failureReason,
        )
        logger.info {
            "source_trace phase=${phase.name} traceId=$traceId provider=$mediaSourceId " +
                "status=${status.name} query=${query.orEmpty()} url=${diagnostics.url.orEmpty()} " +
                "statusCode=${statusCode ?: "-"} elapsedMs=${elapsedMillis ?: "-"} " +
                "parserCount=${parserResultCount ?: "-"} matcherScore=${matcherScore ?: "-"} " +
                "failure=${failureReason.orEmpty()}"
        }
        return diagnostics
    }

    private fun subjectIdFromBrowseSubject(subject: BrowseSubject): String {
        return subject.url.readPluginSubjectMarker()
            ?: subject.url.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: subject.url
    }

    private fun episodeSort(sort: Float?, ep: String?, displayName: String): EpisodeSort? {
        val raw = sort?.toString() ?: ep ?: displayName
        return EpisodeSort(raw).takeIf { it.toString().isNotBlank() }
    }
}

internal data class SelectedSourceSubject(
    val subject: SourceSubject,
    val isExactTitle: Boolean,
    val score: Int,
)

/**
 * Chooses one matching site detail page for a host request. A site search can return several
 * seasons or spin-offs with the same base words; selecting one detail page prevents their
 * episodes from being merged, while the caller keeps every channel listed on that page.
 */
internal fun selectBestSourceSubject(
    subjects: List<SourceSubject>,
    queryNames: List<String>,
): SelectedSourceSubject? {
    val names = queryNames.map(String::trim).filter(String::isNotBlank).distinct()
    if (names.isEmpty()) return null

    val matches = subjects.distinctBy { it.id }.mapIndexedNotNull { subjectIndex, subject ->
        var best: SubjectMatch? = null
        for ((queryIndex, queryName) in names.withIndex()) {
            val query = queryName.normalizeSubjectMatch()
            if (query.isBlank()) continue
            val queryHasVariantMarker = queryName.hasSubjectVariantMarker()
            for ((titleIndex, title) in (listOf(subject.title) + subject.alternativeTitles).withIndex()) {
                val normalizedTitle = title.normalizeSubjectMatch()
                if (normalizedTitle.isBlank()) continue

                val exactTitle = normalizedTitle == query
                val titleIsBaseEquivalent = title.isBaseTitleEquivalent()
                if (!exactTitle && title.hasSubjectVariantMarker() && !queryHasVariantMarker && !titleIsBaseEquivalent) {
                    continue
                }

                val score = when {
                    normalizedTitle == query -> 1_000_000
                    titleIsBaseEquivalent && !queryHasVariantMarker && normalizedTitle.startsWith(query) ->
                        900_000 + query.length
                    query.length >= 3 && normalizedTitle.contains(query) -> 600_000 + query.length
                    normalizedTitle.length >= 3 && query.contains(normalizedTitle) -> 500_000 + normalizedTitle.length
                    else -> null
                } ?: continue

                val candidate = SubjectMatch(
                    score = score - queryIndex * 1_000 - titleIndex * 10 - subjectIndex,
                    isExactTitle = exactTitle,
                )
                if (best?.let { candidate.score > it.score } != false) best = candidate
            }
        }
        best?.let { SelectedSourceSubject(subject, it.isExactTitle, it.score) }
    }

    return matches.maxByOrNull { it.score }
}

private data class SubjectMatch(
    val score: Int,
    val isExactTitle: Boolean,
)

private fun String.normalizeSubjectMatch(): String = lowercase()
    .replace(Regex("(?i)(封面图|封面圖)$"), "")
    .filter(Char::isLetterOrDigit)

private fun String.hasSubjectVariantMarker(): Boolean {
    val lower = lowercase().replace(Regex("(?i)(封面图|封面圖)$"), "")
    return Regex("第[一二三四五六七八九十百0-9]+[季部]").containsMatchIn(lower) ||
        listOf(
            "season", "part", "ova", "oad", "剧场版", "劇場版", "电影", "電影", "movie",
            "日记", "日記", "外传", "外傳", "特别篇", "特別篇", "冰结之绊", "冰結之絆",
            "雪之回忆", "雪之回憶",
        ).any(lower::contains)
}

private fun String.isBaseTitleEquivalent(): Boolean {
    val lower = lowercase().replace(Regex("(?i)(封面图|封面圖)$"), "")
    return Regex("第\\s*(?:一|1)\\s*[季部]").containsMatchIn(lower) ||
        Regex("(?i)\\b(?:first|1st)\\s+season\\b").containsMatchIn(lower) ||
        listOf("新编集版", "新編集版", "新编辑版", "新編集版").any(lower::contains)
}

private const val SUBJECT_MARKER = "#wynime-source-subject=v1:"
private const val EPISODE_MARKER = "#wynime-source-episode=v1:"

private data class PluginEpisodeReference(
    val subjectId: String,
    val channelId: String,
    val episodeId: String,
)

private fun internalSubjectUrl(subjectId: String): String =
    "wynime-source://subject/${subjectId.encodePluginComponent()}" +
        SUBJECT_MARKER + subjectId.encodePluginComponent()

private fun String.withPluginSubjectMarker(subjectId: String): String =
    withoutPluginMarker() + SUBJECT_MARKER + subjectId.encodePluginComponent()

private fun String.withPluginEpisodeMarker(
    subjectId: String,
    channelId: String,
    episodeId: String,
): String = buildString {
    append(withoutPluginMarker())
    append(EPISODE_MARKER)
    append(subjectId.encodePluginComponent())
    append('.')
    append(channelId.encodePluginComponent())
    append('.')
    append(episodeId.encodePluginComponent())
}

private fun String.withoutPluginMarker(): String = substringBefore(SUBJECT_MARKER).substringBefore(EPISODE_MARKER)

private fun String.readPluginSubjectMarker(): String? = substringAfter(SUBJECT_MARKER, "")
    .takeIf { it.isNotBlank() }
    ?.decodePluginComponent()

private fun String.readPluginEpisodeMarker(): PluginEpisodeReference? {
    val encoded = substringAfter(EPISODE_MARKER, "")
    val parts = encoded.split('.')
    if (parts.size != 3) return null
    return runCatching {
        PluginEpisodeReference(
            subjectId = parts[0].decodePluginComponent(),
            channelId = parts[1].decodePluginComponent(),
            episodeId = parts[2].decodePluginComponent(),
        )
    }.getOrNull()
}

private fun String.encodePluginComponent(): String = encodeToByteArray()
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun String.decodePluginComponent(): String {
    require(length % 2 == 0) { "Invalid encoded plugin component" }
    return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        .decodeToString()
}
