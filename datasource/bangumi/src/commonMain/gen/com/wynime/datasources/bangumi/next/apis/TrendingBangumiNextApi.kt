@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.apis

import com.wynime.datasources.bangumi.next.models.BangumiNextErrorResponse
import com.wynime.datasources.bangumi.next.models.BangumiNextGetTrendingSubjects200Response
import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectType

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

open class TrendingBangumiNextApi : ApiClient {

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
    open suspend fun getTrendingSubjects(type: BangumiNextSubjectType, limit: kotlin.Int? = 20, offset: kotlin.Int? = 0): HttpResponse<BangumiNextGetTrendingSubjects200Response> {

        val localVariableAuthNames = listOf<String>("HTTPBearer", "CookiesSession")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        type?.apply { localVariableQuery["type"] = listOf("${ type.value }") }
        limit?.apply { localVariableQuery["limit"] = listOf("$limit") }
        offset?.apply { localVariableQuery["offset"] = listOf("$offset") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/p1/trending/subjects",
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

}
