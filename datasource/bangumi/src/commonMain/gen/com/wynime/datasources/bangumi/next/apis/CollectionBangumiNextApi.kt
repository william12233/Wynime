@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.next.apis

import com.wynime.datasources.bangumi.next.models.BangumiNextCollectSubject
import com.wynime.datasources.bangumi.next.models.BangumiNextCollectionType
import com.wynime.datasources.bangumi.next.models.BangumiNextErrorResponse
import com.wynime.datasources.bangumi.next.models.BangumiNextGetMySubjectCollections200Response
import com.wynime.datasources.bangumi.next.models.BangumiNextSubjectType
import com.wynime.datasources.bangumi.next.models.BangumiNextUpdateSubjectProgress

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

open class CollectionBangumiNextApi : ApiClient {

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
    open suspend fun getMySubjectCollections(subjectType: BangumiNextSubjectType? = null, type: BangumiNextCollectionType? = null, since: kotlin.Int? = null, limit: kotlin.Int? = 20, offset: kotlin.Int? = 0): HttpResponse<BangumiNextGetMySubjectCollections200Response> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        subjectType?.apply { localVariableQuery["subjectType"] = listOf("${ subjectType.value }") }
        type?.apply { localVariableQuery["type"] = listOf("${ type.value }") }
        since?.apply { localVariableQuery["since"] = listOf("$since") }
        limit?.apply { localVariableQuery["limit"] = listOf("$limit") }
        offset?.apply { localVariableQuery["offset"] = listOf("$offset") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/p1/collections/subjects",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = false,
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap()
    }

    @Suppress("UNCHECKED_CAST")
    open suspend fun updateSubjectCollection(subjectID: kotlin.Int, bangumiNextCollectSubject: BangumiNextCollectSubject? = null): HttpResponse<kotlin.String> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody = bangumiNextCollectSubject

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.PUT,
            "/p1/collections/subjects/{subjectID}".replace("{" + "subjectID" + "}", "$subjectID"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = false,
        )

        return jsonRequest(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap()
    }

    @Suppress("UNCHECKED_CAST")
    open suspend fun updateSubjectProgress(subjectID: kotlin.Int, bangumiNextUpdateSubjectProgress: BangumiNextUpdateSubjectProgress? = null): HttpResponse<kotlin.String> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody = bangumiNextUpdateSubjectProgress

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.PATCH,
            "/p1/collections/subjects/{subjectID}".replace("{" + "subjectID" + "}", "$subjectID"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = false,
        )

        return jsonRequest(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap()
    }

}
