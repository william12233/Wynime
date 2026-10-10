package com.wynime.app.domain.sourceplugin

import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.MediaProperties
import com.wynime.datasources.api.matcher.WebVideo
import com.wynime.datasources.api.matcher.WebVideoMatcher
import com.wynime.datasources.api.matcher.WebVideoMatcherContext
import com.wynime.datasources.api.matcher.WebVideoMatcherProvider
import com.wynime.datasources.api.paging.SinglePagePagedSource
import com.wynime.datasources.api.paging.SizedSource
import com.wynime.datasources.api.source.BrowseChannel
import com.wynime.datasources.api.source.BrowseEpisode
import com.wynime.datasources.api.source.BrowseSubject
import com.wynime.datasources.api.source.ConnectionStatus
import com.wynime.datasources.api.source.MatchKind
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.datasources.api.source.MediaMatch
import com.wynime.datasources.api.source.MediaSource
import com.wynime.datasources.api.source.MediaSourceInfo
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.source.plugin.api.SourceConnectionState
import com.wynime.source.plugin.api.SourceDiagnostics
import com.wynime.source.plugin.api.SourceMediaIdentity
import com.wynime.source.plugin.api.SourceResultStatus
import com.wynime.source.plugin.api.SourcePlugin
import com.wynime.source.plugin.api.SourceSearchRequest
import com.wynime.source.plugin.api.SourceSubject
import com.wynime.source.plugin.api.SourceTracePhase
import com.wynime.source.plugin.api.SourceWebResourceMatch
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.platform.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow

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

    override val matcher: WebVideoMatcher = WebVideoMatcher { url, context ->
        when (val match = plugin.matchWebResource(url)) {
            SourceWebResourceMatch.Continue -> WebVideoMatcher.MatchResult.Continue
            SourceWebResourceMatch.LoadPage -> WebVideoMatcher.MatchResult.LoadPage
            is SourceWebResourceMatch.Matched -> {
                val pageDownload = context.media.download as? ResourceLocation.WebVideo
                val sourceDownload = context.media.download as? ResourceLocation.SourcePluginMedia
                val pageUrl = pageDownload?.uri ?: sourceDownload?.uri
                val headers = buildMap {
                    putAll(pageDownload?.headers.orEmpty())
                    putAll(match.headers)
                    if (pageUrl != null && keys.none { it.equals("Referer", ignoreCase = true) }) {
                        put("Referer", pageUrl)
                    }
                }
                WebVideoMatcher.MatchResult.Matched(WebVideo(match.url, headers))
            }
        }
    }

    override suspend fun checkConnection(): ConnectionStatus = when (plugin.checkConnection().state) {
        SourceConnectionState.CONNECTED -> ConnectionStatus.SUCCESS
        SourceConnectionState.AUTH_REQUIRED,
        SourceConnectionState.BLOCKED,
        SourceConnectionState.FAILED,
        -> ConnectionStatus.FAILED
    }

    private suspend fun searchPluginSubjects(
        query: String,
        traceId: String,
        entryPoint: String,
    ): List<SourceSubject> {
        trace(traceId, SourceTracePhase.SEARCH_REQUEST, SourceResultStatus.SUCCESS, query = query)
        val searchResults = try {
            plugin.search(SourceSearchRequest(query, traceId = traceId, entryPoint = entryPoint))
        } catch (error: SourcePluginFailure) {
            traceFailure(traceId, SourceTracePhase.SEARCH_RESPONSE, error)
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw sourcePluginBoundaryFailure(
                traceId = traceId,
                provider = mediaSourceId,
                entryPoint = entryPoint,
                fallbackStatus = SourceResultStatus.PARSE_ERROR,
                error = error,
                url = query,
                retryable = false,
            )
        }
        trace(
            traceId,
            SourceTracePhase.SEARCH_RESPONSE,
            if (searchResults.isEmpty()) SourceResultStatus.SUBJECT_NO_MATCH else SourceResultStatus.SUCCESS,
            query = query,
            parserResultCount = searchResults.size,
        )
        return searchResults
    }

    override suspend fun searchSubjects(keyword: String): List<BrowseSubject> {
        val traceId = Uuid.randomString()
        val requestedTitle = sourceTitleMatch(keyword)
        val requestedNames = sourceTitleNamesForRequest(listOf(keyword))
        val subjects = buildList {
            val seen = HashSet<String>()
            var completedQuery = false
            var lastFailure: SourcePluginFailure? = null
            for (query in sourceSearchQueryVariantsForRequest(listOf(keyword))) {
                val searchResults = try {
                    searchPluginSubjects(query, traceId, "browse-search")
                } catch (error: SourcePluginFailure) {
                    lastFailure = error
                    continue
                }
                completedQuery = true
                for (subject in searchResults) {
                    if (seen.add(subject.id)) add(subject)
                }
                if (requestedTitle.hasVariantMarker &&
                    selectBestSourceSubject(this, requestedNames)?.isExactTitle == true
                ) {
                    break
                }
            }
            if (!completedQuery) lastFailure?.let { throw it }
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
            val requestedNames = buildList {
                addAll(query.subjectNames)
                query.subjectNameCN?.let(::add)
            }
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
            val matchingNames = sourceTitleNamesForRequest(requestedNames)
            val searchQueries = sourceSearchQueryVariantsForRequest(requestedNames)
            trace(
                traceId,
                SourceTracePhase.DISCOVERY_START,
                SourceResultStatus.SUCCESS,
                query = requestedNames.joinToString(" | "),
            )
            val subjects = buildList {
                val seen = HashSet<String>()
                var completedQuery = false
                var lastFailure: SourcePluginFailure? = null
                for (searchQuery in searchQueries) {
                    val searchResults = try {
                        searchPluginSubjects(searchQuery, traceId, "discovery-search")
                    } catch (error: SourcePluginFailure) {
                        lastFailure = error
                        continue
                    }
                    completedQuery = true
                    for (subject in searchResults) {
                        if (seen.add(subject.id)) add(subject)
                    }
                    if (selectBestSourceSubject(this, matchingNames)?.isExactTitle == true) break
                }
                if (!completedQuery) lastFailure?.let { throw it }
            }
            val selectedSubject = selectBestSourceSubject(subjects, matchingNames)
            if (selectedSubject == null) {
                val diagnostics = trace(
                    traceId,
                    SourceTracePhase.MATCH_RESULT,
                    SourceResultStatus.SUBJECT_NO_MATCH,
                    query = requestedNames.joinToString(" | "),
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
                query = requestedNames.joinToString(" | "),
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

internal fun selectBestSourceSubject(
    subjects: List<SourceSubject>,
    queryNames: List<String>,
): SelectedSourceSubject? {
    val names = queryNames.map(String::trim).filter(String::isNotBlank).distinct()
    if (names.isEmpty()) return null

    val matches = subjects.distinctBy { it.id }.mapIndexedNotNull { subjectIndex, subject ->
        var best: SubjectMatch? = null
        for ((queryIndex, queryName) in names.withIndex()) {
            val query = sourceTitleMatch(queryName)
            if (query.canonical.isBlank()) continue
            for ((titleIndex, title) in (listOf(subject.title) + subject.alternativeTitles).withIndex()) {
                val normalizedTitle = sourceTitleMatch(title)
                if (normalizedTitle.canonical.isBlank()) continue

                val exactTitle = normalizedTitle.canonical == query.canonical
                if (query.variant != null && normalizedTitle.variant != query.variant) {
                    continue
                }
                if (query.variant == null && normalizedTitle.variant != null &&
                    !normalizedTitle.isBaseEquivalent
                ) {
                    continue
                }
                if (query.arcMarkers.isNotEmpty() &&
                    normalizedTitle.arcMarkers.isNotEmpty() &&
                    normalizedTitle.arcMarkers != query.arcMarkers
                ) {
                    continue
                }
                if (!query.hasVariantMarker && normalizedTitle.hasVariantMarker && !normalizedTitle.isBaseEquivalent) {
                    continue
                }

                val score = when {
                    exactTitle -> 1_000_000
                    normalizedTitle.isBaseEquivalent && !query.hasVariantMarker &&
                        normalizedTitle.base.startsWith(query.base) ->
                        900_000 + query.base.length
                    query.base.length >= 3 && normalizedTitle.base.contains(query.base) ->
                        600_000 + query.base.length
                    normalizedTitle.base.length >= 3 && query.base.contains(normalizedTitle.base) ->
                        500_000 + normalizedTitle.base.length
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
