package com.wynime.datasources.ikaros

import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.flow.flowOf
import com.wynime.datasources.api.paging.SizedSource
import com.wynime.datasources.api.source.ConnectionStatus
import com.wynime.datasources.api.source.FactoryId
import com.wynime.datasources.api.source.HttpMediaSource
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.datasources.api.source.MediaMatch
import com.wynime.datasources.api.source.MediaSource
import com.wynime.datasources.api.source.MediaSourceConfig
import com.wynime.datasources.api.source.MediaSourceFactory
import com.wynime.datasources.api.source.MediaSourceInfo
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.get
import com.wynime.datasources.api.source.parameter.MediaSourceParameters
import com.wynime.datasources.api.source.parameter.MediaSourceParametersBuilder
import com.wynime.utils.ktor.ScopedHttpClient
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class IkarosMediaSource(
    override val mediaSourceId: String,
    config: MediaSourceConfig,
    httpClient: ScopedHttpClient,
) : HttpMediaSource() {
    companion object {
        const val ID = "ikaros"
        val INFO = MediaSourceInfo(
            displayName = "Ikaros",
            description = "专注于 ACGMN 的内容管理系统 (CMS)",
            websiteUrl = "https://docs.ikaros.run",
            iconUrl = "https://docs.ikaros.run/img/favicon.ico",
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private val client = IkarosClient(
        config[Parameters.baseUrl],
        httpClient,
        addAuthorizationHeaders = {
            val username = config[Parameters.username]
            val password = config[Parameters.password]
            header(
                HttpHeaders.Authorization,
                "Basic " + Base64.encode(
                    "$username:$password".toByteArray(),
                ),
            )
        },
    )

    object Parameters : MediaSourceParametersBuilder() {
        val baseUrl = string("baseUrl", description = "API base URL")
        val username = string("username", description = "用户名")
        val password = string("password", description = "密码")
    }

    class Factory : MediaSourceFactory {
        override val factoryId: FactoryId get() = FactoryId(ID)

        override val parameters: MediaSourceParameters = Parameters.build()
        override val allowMultipleInstances: Boolean get() = true
        override fun create(
            mediaSourceId: String,
            config: MediaSourceConfig,
            client: ScopedHttpClient
        ): MediaSource =
            IkarosMediaSource(mediaSourceId, config, client)

        override val info: MediaSourceInfo get() = INFO
    }

    override val kind: MediaSourceKind get() = MediaSourceKind.WEB
    override val info: MediaSourceInfo get() = INFO

    override suspend fun checkConnection(): ConnectionStatus {
        return if ((HttpStatusCode.OK == client.checkConnection())
        ) ConnectionStatus.SUCCESS else ConnectionStatus.FAILED
    }

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> {
        val emptySizeSource = IkarosSizeSource(
            totalSize = flowOf(0), finished = flowOf(false), results = flowOf(),
        )
        try {
            val bgmTvSubjectId = checkNotNull(query.subjectId)
            val subjectSyncs = client.getSubjectSyncsWithBgmTvSubjectId(bgmTvSubjectId)
            if (subjectSyncs.isEmpty()) {
                return emptySizeSource
            }
            val subjectId = subjectSyncs[0].subjectId
            val episodeRecords = client.getEpisodeRecordsWithId(subjectId.toString())
            if (episodeRecords.isEmpty()) {
                return emptySizeSource
            }
            return client.episodeRecords2SizeSource(subjectId.toString(), episodeRecords)
        } catch (exception: RuntimeException) {
            logger.error("Request fail: ", exception)
        }
        return emptySizeSource
    }
}
