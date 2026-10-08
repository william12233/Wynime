@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.apis

import com.wynime.datasources.bangumi.next.models.BangumiNextCreateEpisodeComment200Response
import com.wynime.datasources.bangumi.next.models.BangumiNextCreateEpisodeCommentRequest
import com.wynime.datasources.bangumi.next.models.BangumiNextEpisode
import com.wynime.datasources.bangumi.next.models.BangumiNextErrorResponse
import com.wynime.datasources.bangumi.next.models.BangumiNextGetEpisodeComments200ResponseInner
import com.wynime.datasources.bangumi.next.models.BangumiNextUpdateContent

import com.wynime.datasources.bangumi.next.infrastructure.*
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.request.forms.formData
import io.ktor.client.engine.HttpClientEngine
import kotlinx.serialization.json.Json
import io.ktor.http.ParametersBuilder
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

open class EpisodeBangumiNextApi : ApiClient {

    constructor(
        baseUrl: String = ApiClient.BASE_URL,
        httpClientEngine: HttpClientEngine? = null,
        httpClientConfig: ((HttpClientConfig<*>) -> Unit)? = null,
        jsonSerializer: Json = ApiClient.JSON_DEFAULT
    ) : super(baseUrl = baseUrl, httpClientEngine = httpClientEngine, httpClientConfig = httpClientConfig, jsonBlock = jsonSerializer)

    constructor(
        baseUrl: String,
        httpClient: HttpClient
    ): super(baseUrl = baseUrl, httpClient = httpClient)

    @Suppress("UNCHECKED_CAST")
    open suspend fun createEpisodeComment(episodeID: kotlin.Int, bangumiNextCreateEpisodeCommentRequest: BangumiNextCreateEpisodeCommentRequest? = null): HttpResponse<BangumiNextCreateEpisodeComment200Response> {

        val localVariableAuthNames = listOf<String>("HTTPBearer", "CookiesSession")

        val localVariableBody = bangumiNextCreateEpisodeCommentRequest

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.POST,
            "/p1/episodes/{episodeID}/comments".replace("{" + "episodeID" + "}", "$episodeID"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
        )

        return jsonRequest(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap()
    }

    @Suppress("UNCHECKED_CAST")
    open suspend fun deleteEpisodeComment(commentID: kotlin.Int): HttpResponse<kotlin.String> {

        val localVariableAuthNames = listOf<String>("HTTPBearer", "CookiesSession")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.DELETE,
            "/p1/episodes/-/comments/{commentID}".replace("{" + "commentID" + "}", "$commentID"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap()
    }

    @Suppress("UNCHECKED_CAST")
    open suspend fun getEpisode(episodeID: kotlin.Int): HttpResponse<BangumiNextEpisode> {

        val localVariableAuthNames = listOf<String>("HTTPBearer", "CookiesSession")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/p1/episodes/{episodeID}".replace("{" + "episodeID" + "}", "$episodeID"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap()
    }

    @Suppress("UNCHECKED_CAST")
    open suspend fun getEpisodeComments(episodeID: kotlin.Int): HttpResponse<kotlin.collections.List<BangumiNextGetEpisodeComments200ResponseInner>> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/p1/episodes/{episodeID}/comments".replace("{" + "episodeID" + "}", "$episodeID"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = false,
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap<GetEpisodeCommentsResponse>().map { value }
    }

    @Serializable(GetEpisodeCommentsResponse.Companion::class)
    private class GetEpisodeCommentsResponse(val value: List<BangumiNextGetEpisodeComments200ResponseInner>) {
        companion object : KSerializer<GetEpisodeCommentsResponse> {
            private val serializer: KSerializer<List<BangumiNextGetEpisodeComments200ResponseInner>> = serializer<List<BangumiNextGetEpisodeComments200ResponseInner>>()
            override val descriptor = serializer.descriptor
            override fun serialize(encoder: Encoder, value: GetEpisodeCommentsResponse) = serializer.serialize(encoder, value.value)
            override fun deserialize(decoder: Decoder) = GetEpisodeCommentsResponse(serializer.deserialize(decoder))
        }
    }

    @Suppress("UNCHECKED_CAST")
    open suspend fun updateEpisodeComment(commentID: kotlin.Int, bangumiNextUpdateContent: BangumiNextUpdateContent? = null): HttpResponse<kotlin.String> {

        val localVariableAuthNames = listOf<String>("HTTPBearer", "CookiesSession")

        val localVariableBody = bangumiNextUpdateContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.PUT,
            "/p1/episodes/-/comments/{commentID}".replace("{" + "commentID" + "}", "$commentID"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
        )

        return jsonRequest(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap()
    }

}
