/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.sourceplugin

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
import me.him188.ani.source.plugin.api.SourcePlugin
import me.him188.ani.source.plugin.api.SourceSearchRequest
import me.him188.ani.source.plugin.api.SourceMediaIdentity
import me.him188.ani.source.plugin.api.SourceSubject
import me.him188.ani.source.plugin.api.SourceWebResourceMatch
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger

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

    override suspend fun searchSubjects(keyword: String): List<BrowseSubject> = plugin.search(
        SourceSearchRequest(keyword),
    ).map { subject ->
        BrowseSubject(
            name = subject.title,
            url = subject.detailUrl?.withPluginSubjectMarker(subject.id)
                ?: internalSubjectUrl(subject.id),
        )
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
            logger.info { "Source plugin fetch started: $mediaSourceId" }
            val names = query.subjectNames.ifEmpty { listOfNotNull(query.subjectNameCN) }
            val subjects = buildList {
                val seen = HashSet<String>()
                for (name in names) {
                    val searchResults = plugin.search(SourceSearchRequest(name))
                    logger.info { "Source plugin search finished: $mediaSourceId name=$name results=${searchResults.size}" }
                    for (subject in searchResults) {
                        if (seen.add(subject.id)) add(subject)
                    }
                }
            }
            val selectedSubject = selectBestSourceSubject(subjects, names)
            if (selectedSubject == null) {
                logger.info {
                    "Source plugin subject selection produced no safe match: " +
                        "$mediaSourceId names=${names.size} candidates=${subjects.size}"
                }
                return@flow
            }
            val subject = selectedSubject.subject
            logger.info {
                "Source plugin detail page selected: $mediaSourceId id=${subject.id} " +
                    "title=${subject.title} exact=${selectedSubject.isExactTitle} candidates=${subjects.size}; " +
                    "all detail-page channels will be retained"
            }
            var matchCount = 0
            val details = plugin.getSubject(subject.id)
            logger.info { "Source plugin details finished: $mediaSourceId subject=${subject.id} channels=${details.channels.size}" }
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
            logger.info { "Source plugin fetch finished: $mediaSourceId matches=$matchCount" }
        }
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
        val subjectHasVariantMarker = subject.title.hasSubjectVariantMarker()
        for ((queryIndex, queryName) in names.withIndex()) {
            val query = queryName.normalizeSubjectMatch()
            if (query.isBlank()) continue
            val queryHasVariantMarker = queryName.hasSubjectVariantMarker()
            for ((titleIndex, title) in (listOf(subject.title) + subject.alternativeTitles).withIndex()) {
                val normalizedTitle = title.normalizeSubjectMatch()
                if (normalizedTitle.isBlank()) continue

                val exactTitle = title == subject.title && normalizedTitle == query
                if (!exactTitle && subjectHasVariantMarker && !queryHasVariantMarker) continue

                val score = when {
                    normalizedTitle == query -> 1_000_000
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

private fun String.normalizeSubjectMatch(): String = lowercase().filter(Char::isLetterOrDigit)

private fun String.hasSubjectVariantMarker(): Boolean {
    val lower = lowercase()
    return Regex("第[一二三四五六七八九十百0-9]+[季部]").containsMatchIn(lower) ||
        listOf(
            "season", "part", "ova", "oad", "剧场版", "劇場版", "电影", "電影", "movie",
            "日记", "日記", "外传", "外傳", "特别篇", "特別篇",
        ).any(lower::contains)
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
