package com.wynime.datasources.ikaros

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.MediaExtraFiles
import com.wynime.datasources.api.MediaProperties
import com.wynime.datasources.api.Subtitle
import com.wynime.datasources.api.SubtitleKind
import com.wynime.datasources.api.paging.SizedSource
import com.wynime.datasources.api.source.MatchKind
import com.wynime.datasources.api.source.MediaMatch
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.FileSize.Companion.bytes
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.datasources.api.topic.titles.RawTitleParser
import com.wynime.datasources.api.topic.titles.parse
import com.wynime.datasources.ikaros.models.IkarosEpisodeGroup
import com.wynime.datasources.ikaros.models.IkarosEpisodeMeta
import com.wynime.datasources.ikaros.models.IkarosEpisodeRecord
import com.wynime.datasources.ikaros.models.IkarosEpisodeResource
import com.wynime.datasources.ikaros.models.IkarosSubjectSync
import com.wynime.datasources.ikaros.models.IkarosVideoSubtitle
import com.wynime.utils.ktor.ScopedHttpClient
import com.wynime.utils.logging.error
import com.wynime.utils.logging.logger
import models.IkarosAttachment

class IkarosClient(
    private val baseUrl: String,
    private val client: ScopedHttpClient,
    private val addAuthorizationHeaders: io.ktor.http.HttpMessageBuilder.() -> Unit,
) {
    companion object {
        private val logger = logger<IkarosClient>()
        private val json = Json { ignoreUnknownKeys = true }
        private const val API_VERSION = "v1alpha1"

        private const val RESOURCE_CONCURRENCY = 4
    }

    suspend fun checkConnection(): HttpStatusCode {
        return try {
            client.use {
                get(baseUrl) {
                    addAuthorizationHeaders()
                }.run {
                    check(status.isSuccess()) { "Request failed: $this" }
                }
            }
            HttpStatusCode.OK
        } catch (e: Exception) {
            logger.error(e) { "Failed to connect to $baseUrl" }
            HttpStatusCode.ServiceUnavailable
        }
    }

    suspend fun getSubjectSyncsWithBgmTvSubjectId(bgmTvSubjectId: String): List<IkarosSubjectSync> {
        if (bgmTvSubjectId.isBlank() || bgmTvSubjectId.toInt() <= 0) {
            return emptyList()
        }
        val url = "$baseUrl/api/$API_VERSION/subject/syncs/platform?platform=BGM_TV&platformId=$bgmTvSubjectId"
        val responseText = client.use {
            get(url) {
                addAuthorizationHeaders()
            }.bodyAsText()
        }
        return json.decodeFromString(responseText)
    }

    fun episodeRecords2SizeSource(
        subjectId: String,
        episodeRecords: List<IkarosEpisodeRecord>,
    ): SizedSource<MediaMatch> {
        val entries = episodeRecords.flatMap { record ->
            val sort = episodeSortOf(record.episode) ?: return@flatMap emptyList()
            record.resources.orEmpty().map { resource -> sort to resource }
        }
        val results = channelFlow {
            val semaphore = Semaphore(RESOURCE_CONCURRENCY)
            for ((sort, resource) in entries) {
                launch {
                    semaphore.withPermit {
                        send(MediaMatch(resourceToMedia(subjectId, sort, resource), MatchKind.EXACT))
                    }
                }
            }
        }
        return IkarosSizeSource(
            totalSize = flowOf(entries.size), finished = flowOf(true), results = results,
        )
    }

    private suspend fun resourceToMedia(
        subjectId: String,
        sort: EpisodeSort,
        epRes: IkarosEpisodeResource,
    ): DefaultMedia {
        val attachment: IkarosAttachment? = getAttachmentById(epRes.attachmentId)
        val parseResult = RawTitleParser.getDefault().parse(epRes.name)
        return DefaultMedia(
            mediaId = epRes.attachmentId.toString(),
            mediaSourceId = IkarosMediaSource.ID,
            originalUrl = baseUrl.plus("/console/#/subjects/subject/details/").plus(subjectId),
            download = ResourceLocation.HttpStreamingFile(
                uri = getAttReadUrl(epRes.attachmentId),
            ),
            originalTitle = epRes.name,
            publishedTime = kotlin.runCatching {
                DateFormater.utcDateStr2timeStamp(attachment?.updateTime ?: "")
            }.getOrElse { 0 },
            properties = MediaProperties(
                subjectName = null,
                episodeName = null,
                subtitleLanguageIds = parseResult.subtitleLanguages.map { it.id },
                resolution = parseResult.resolution?.displayName ?: "480P",
                alliance = IkarosMediaSource.ID,
                size = (attachment?.size ?: 0).bytes,
                subtitleKind = SubtitleKind.EXTERNAL_PROVIDED,
            ),
            episodeRange = EpisodeRange.single(sort),
            location = MediaSourceLocation.Online,
            kind = MediaSourceKind.WEB,
            extraFiles = fetchVideoAttSubtitles2ExtraFiles(epRes.attachmentId),
        )
    }

    private fun episodeSortOf(episode: IkarosEpisodeMeta): EpisodeSort? {
        val number = episode.sequence.let { seq ->
            if (seq == seq.toLong().toDouble()) seq.toLong().toString() else seq.toString()
        }
        val type = when (episode.group) {
            IkarosEpisodeGroup.MAIN -> return EpisodeSort(number)
            IkarosEpisodeGroup.SPECIAL_PROMOTION -> EpisodeType.SP
            IkarosEpisodeGroup.OPENING_SONG -> EpisodeType.OP
            IkarosEpisodeGroup.ENDING_SONG -> EpisodeType.ED
            IkarosEpisodeGroup.PROMOTION_VIDEO -> EpisodeType.PV
            IkarosEpisodeGroup.SMALL_THEATER -> EpisodeType.MAD
            IkarosEpisodeGroup.ORIGINAL_VIDEO_ANIMATION -> EpisodeType.OVA
            IkarosEpisodeGroup.ORIGINAL_ANIMATION_DISC -> EpisodeType.OAD
            else -> return null
        }
        return EpisodeSort(type.value + number)
    }

    suspend fun getAttReadUrl(attachmentId: Long): String {
        val url = "$baseUrl/api/$API_VERSION/attachment/url/read/id/$attachmentId"
        val responseText = client.use {
            get(url) {
                addAuthorizationHeaders()
            }.bodyAsText()
        }
        return getResUrl(responseText)
    }

    suspend fun getEpisodeRecordsWithId(subjectId: String): List<IkarosEpisodeRecord> {
        if (subjectId.isBlank() || subjectId.toInt() <= 0) {
            return emptyList()
        }
        val url = "$baseUrl/api/$API_VERSION/episode/records/subjectId/$subjectId"
        val responseText = client.use {
            get(url) {
                addAuthorizationHeaders()
            }.bodyAsText()
        }
        return json.decodeFromString(responseText)
    }

    private suspend fun getAttachmentById(attId: Long): IkarosAttachment? {
        if (attId <= 0) return null
        val url = baseUrl.plus("/api/$API_VERSION/attachment/").plus(attId)
        return client.use {
            get(url) {
                addAuthorizationHeaders()
            }.body<IkarosAttachment>()
        }
    }

    private suspend fun getAttachmentVideoSubtitlesById(attId: Long): List<IkarosVideoSubtitle>? {
        if (attId <= 0) return null
        val url = baseUrl.plus("/api/$API_VERSION/attachment/relation/videoSubtitle/subtitles/").plus(attId)
        val responseText = client.use {
            get(url) {
                addAuthorizationHeaders()
            }.bodyAsText()
        }
        return json.decodeFromString(responseText)
    }

    private fun getResUrl(url: String): String {
        if (url.isEmpty()) {
            return ""
        }
        @Suppress("HttpUrlsUsage")
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
        return baseUrl + url
    }

    private suspend fun fetchVideoAttSubtitles2ExtraFiles(attachmentId: Long): MediaExtraFiles {
        if (attachmentId <= 0) return MediaExtraFiles()
        val attVideoSubtitleList = getAttachmentVideoSubtitlesById(attachmentId)
        val subtitles: MutableList<Subtitle> = mutableListOf()
        if (!attVideoSubtitleList.isNullOrEmpty()) {
            for (ikVideoSubtitle in attVideoSubtitleList) {

                subtitles.add(
                    Subtitle(
                        uri = getAttReadUrl(ikVideoSubtitle.attachmentId),
                        language = AssNameParser.default.parseAssName2Language(ikVideoSubtitle.name),
                        mimeType = AssNameParser.httpMineType,
                    ),
                )
            }
        }
        return MediaExtraFiles(subtitles)
    }
}

class IkarosSizeSource(
    override val results: Flow<MediaMatch>, override val finished: Flow<Boolean>, override val totalSize: Flow<Int?>
) : SizedSource<MediaMatch>

