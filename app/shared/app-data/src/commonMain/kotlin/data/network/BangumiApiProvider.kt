package com.wynime.app.data.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import com.wynime.app.data.repository.user.AccessTokenSession
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.datasources.bangumi.apis.DefaultApi
import com.wynime.datasources.bangumi.infrastructure.HttpResponse
import com.wynime.datasources.bangumi.models.BangumiUser
import com.wynime.datasources.bangumi.models.BangumiSubjectType
import com.wynime.datasources.bangumi.next.apis.EpisodeBangumiNextApi
import com.wynime.datasources.bangumi.next.apis.SubjectBangumiNextApi
import com.wynime.datasources.bangumi.next.infrastructure.HttpResponse as NextHttpResponse
import com.wynime.utils.ktor.ScopedHttpClient
import com.wynime.utils.ktor.UnsafeScopedHttpClientApi
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger

class BangumiApiProvider(
    private val client: ScopedHttpClient,
    private val tokenRepository: TokenRepository,
) : BangumiExploreDataSource {
    suspend fun hasAccessToken(): Boolean = currentAccessToken() != null

    suspend fun currentUsername(): String? {
        return currentUser()?.username
    }

    suspend fun currentUser(): BangumiUser? {
        if (!hasAccessToken()) return null
        return request { getMyself() }
    }

    suspend fun <T : Any> request(block: suspend DefaultApi.() -> HttpResponse<T>): T {
        return withApi { block(this).body() }
    }

    suspend fun <T : Any> nextSubjectRequest(block: suspend SubjectBangumiNextApi.() -> NextHttpResponse<T>): T {
        return withHttpClient { httpClient ->
            val api = SubjectBangumiNextApi(
                baseUrl = BANGUMI_API_BASE_URL,
                httpClient = httpClient,
            )
            currentAccessToken()?.let(api::setBearerToken)
            block(api).body()
        }
    }

    suspend fun <T : Any> nextEpisodeRequest(block: suspend EpisodeBangumiNextApi.() -> NextHttpResponse<T>): T {
        return withHttpClient { httpClient ->
            val api = EpisodeBangumiNextApi(
                baseUrl = BANGUMI_API_BASE_URL,
                httpClient = httpClient,
            )
            currentAccessToken()?.let(api::setBearerToken)
            block(api).body()
        }
    }

    override suspend fun getTrendingSubjects(limit: Int, offset: Int): BangumiTrendingPage {
        val endpoint = "$BANGUMI_NEXT_API_BASE_URL/p1/trending/subjects"
        return getJson<RawTrendingPage>(endpoint, "trending subjects") {
            parameter("type", 2)
            parameter("limit", limit)
            parameter("offset", offset)
        }.toBangumiTrendingPage()
    }

    override suspend fun getCalendarDays(): List<BangumiCalendarDay> {
        val endpoint = "$BANGUMI_API_BASE_URL/calendar"
        return getJson<List<RawCalendarDay>>(endpoint, "calendar") { }.map { day ->
            BangumiCalendarDay(
                weekdayId = day.weekday?.id ?: 0,
                items = day.items.mapNotNull { item -> item.toBangumiCalendarEntry() },
            )
        }
    }

    override suspend fun getEpisodes(subjectId: Int): List<BangumiExploreEpisode> {
        return request {
            getEpisodes(subjectId = subjectId, limit = 200, offset = 0)
        }.data.orEmpty().map { episode ->
            BangumiExploreEpisode(
                id = episode.id,
                type = episode.type,
                name = episode.name,
                nameCn = episode.nameCn,
                sort = episode.sort.toString(),
                ep = episode.ep?.toString(),
                airDate = episode.airdate.toLocalDateOrNull(),
            )
        }
    }

    override suspend fun getCollectionPreferences(): List<BangumiCollectionPreference> {
        val username = currentUsername() ?: return emptyList()
        val result = ArrayList<BangumiCollectionPreference>()
        var offset = 0
        val limit = 100
        while (true) {
            val page = request {
                getUserCollectionsByUsername(
                    username = username,
                    subjectType = BangumiSubjectType.Anime,
                    limit = limit,
                    offset = offset,
                )
            }
            val data = page.data.orEmpty()
            result += data.map { collection ->
                BangumiCollectionPreference(
                    subjectId = collection.subjectId,
                    collectionType = collection.type.value,
                    tags = collection.tags,
                    subjectTags = collection.subject?.tags.orEmpty().map { it.name },
                    score = collection.rate,
                    nsfw = false,
                )
            }
            if (data.isEmpty() || data.size < limit || result.size >= (page.total ?: result.size)) break
            offset += data.size
        }
        return result
    }

    private suspend fun <T> withApi(block: suspend DefaultApi.() -> T): T {
        return withHttpClient { httpClient ->
            val api = DefaultApi(
                baseUrl = BANGUMI_API_BASE_URL,
                httpClient = httpClient,
            )
            currentAccessToken()?.let(api::setBearerToken)
            block(api)
        }
    }

    private suspend inline fun <reified T> getJson(
        endpoint: String,
        operation: String,
        crossinline configure: HttpRequestBuilder.() -> Unit,
    ): T = withHttpClient { httpClient ->
        val response = httpClient.get(endpoint, configure)
        val text = response.bodyAsText()
        logger.info {
            "Bangumi explore response received: operation=$operation, status=${response.status.value}, bytes=${text.length}."
        }
        if (!response.status.isSuccess()) {
            throw BangumiExploreRequestException(
                operation = operation,
                endpoint = endpoint,
                statusCode = response.status.value,
                message = "HTTP ${response.status.value} ${response.status.description}",
            )
        }
        try {
            val result = bangumiJson.decodeFromString<T>(text)
            logger.info { "Bangumi explore response parsed: operation=$operation." }
            result
        } catch (e: SerializationException) {
            throw BangumiExploreRequestException(
                operation = operation,
                endpoint = endpoint,
                statusCode = response.status.value,
                message = "response parse failure",
                cause = e,
            )
        }
    }

    @OptIn(UnsafeScopedHttpClientApi::class)
    private suspend fun <T> withHttpClient(block: suspend (HttpClient) -> T): T {
        val ticket = client.borrow()
        try {
            return block(ticket.client)
        } finally {
            client.returnClient(ticket)
        }
    }

    private suspend fun currentAccessToken(): String? = tokenRepository.session.first()
        .let { it as? AccessTokenSession }
        ?.tokens
        ?.bangumiAccessToken
        ?.takeIf(String::isNotBlank)

    private companion object {
        const val BANGUMI_API_BASE_URL = "https://api.bgm.tv"
        const val BANGUMI_NEXT_API_BASE_URL = "https://next.bgm.tv"
        val bangumiJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        val logger = logger<BangumiApiProvider>()
    }
}

class BangumiExploreRequestException(
    val operation: String,
    val endpoint: String,
    val statusCode: Int?,
    message: String,
    cause: Throwable? = null,
) : Exception("$operation failed at $endpoint${statusCode?.let { " (HTTP $it)" } ?: ""}: $message", cause)

@Serializable
private data class RawTrendingPage(
    @SerialName("data") val data: List<RawTrendingItem> = emptyList(),
    @SerialName("total") val total: Int = 0,
)

@Serializable
private data class RawTrendingItem(
    @SerialName("count") val count: Int = 0,
    @SerialName("subject") val subject: RawTrendingSubject? = null,
)

@Serializable
private data class RawTrendingSubject(
    @SerialName("id") val id: Int? = null,
    @SerialName("type") val type: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("nameCN") val nameCn: String? = null,
    @SerialName("nsfw") val nsfw: Boolean = false,
    @SerialName("rating") val rating: RawRating? = null,
    @SerialName("images") val images: RawImages? = null,
)

@Serializable
private data class RawRating(
    @SerialName("score") val score: Double? = null,
    @SerialName("total") val total: Int? = null,
    @SerialName("rank") val rank: Int? = null,
)

@Serializable
private data class RawCalendarDay(
    @SerialName("weekday") val weekday: RawCalendarWeekday? = null,
    @SerialName("items") val items: List<RawCalendarSubject> = emptyList(),
)

@Serializable
private data class RawCalendarWeekday(
    @SerialName("id") val id: Int? = null,
)

@Serializable
private data class RawCalendarSubject(
    @SerialName("id") val id: Int? = null,
    @SerialName("type") val type: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("name_cn") val nameCn: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("images") val images: RawImages? = null,
    @SerialName("nsfw") val nsfw: Boolean = false,
)

@Serializable
private data class RawImages(
    @SerialName("large") val large: String? = null,
    @SerialName("common") val common: String? = null,
    @SerialName("medium") val medium: String? = null,
    @SerialName("small") val small: String? = null,
)

private fun RawTrendingPage.toBangumiTrendingPage(): BangumiTrendingPage {
    return BangumiTrendingPage(
        subjects = data.mapNotNull { item ->
            val subject = item.subject ?: return@mapNotNull null
            val id = subject.id?.takeIf { it > 0 } ?: return@mapNotNull null
            BangumiExploreSubject(
                id = id,
                name = subject.name.orEmpty(),
                nameCn = subject.nameCn.orEmpty(),
                imageLarge = subject.images?.large
                    ?: subject.images?.common
                    ?: subject.images?.medium
                    ?: subject.images?.small
                    .orEmpty(),
                type = subject.type ?: 2,
                nsfw = subject.nsfw,
                score = subject.rating?.score ?: 0.0,
                scoreCount = subject.rating?.total ?: 0,
                rank = subject.rating?.rank ?: 0,
                trendingCount = item.count,
            )
        },
        total = total,
    )
}

private fun RawCalendarSubject.toBangumiCalendarEntry(): BangumiCalendarEntry? {
    val id = id?.takeIf { it > 0 } ?: return null
    return BangumiCalendarEntry(
        id = id,
        name = name.orEmpty(),
        nameCn = nameCn.orEmpty(),
        imageLarge = images?.large
            ?: images?.common
            ?: images?.medium
            ?: images?.small
            .orEmpty(),
        type = type ?: 0,
        nsfw = nsfw,
        airDate = airDate?.toLocalDateOrNull(),
    )
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
