@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.wynime.datasources.bangumi.apis

import com.wynime.datasources.bangumi.models.BangumiCharacterDetail
import com.wynime.datasources.bangumi.models.BangumiCharacterPerson
import com.wynime.datasources.bangumi.models.BangumiCharacterRevision
import com.wynime.datasources.bangumi.models.BangumiDetailedRevision
import com.wynime.datasources.bangumi.models.BangumiEpType
import com.wynime.datasources.bangumi.models.BangumiEpisodeDetail
import com.wynime.datasources.bangumi.models.BangumiErrorDetail
import com.wynime.datasources.bangumi.models.BangumiGetUserSubjectEpisodeCollection200Response
import com.wynime.datasources.bangumi.models.BangumiIndex
import com.wynime.datasources.bangumi.models.BangumiIndexBasicInfo
import com.wynime.datasources.bangumi.models.BangumiIndexSubjectAddInfo
import com.wynime.datasources.bangumi.models.BangumiIndexSubjectEditInfo
import com.wynime.datasources.bangumi.models.BangumiPagedEpisode
import com.wynime.datasources.bangumi.models.BangumiPagedRevision
import com.wynime.datasources.bangumi.models.BangumiPagedUserCollection
import com.wynime.datasources.bangumi.models.BangumiPatchUserSubjectEpisodeCollectionRequest
import com.wynime.datasources.bangumi.models.BangumiPersonCharacter
import com.wynime.datasources.bangumi.models.BangumiPersonDetail
import com.wynime.datasources.bangumi.models.BangumiPersonRevision
import com.wynime.datasources.bangumi.models.BangumiPutUserEpisodeCollectionRequest
import com.wynime.datasources.bangumi.models.BangumiRelatedCharacter
import com.wynime.datasources.bangumi.models.BangumiRelatedPerson
import com.wynime.datasources.bangumi.models.BangumiSearchSubjects200Response
import com.wynime.datasources.bangumi.models.BangumiSearchSubjectsRequest
import com.wynime.datasources.bangumi.models.BangumiSubject
import com.wynime.datasources.bangumi.models.BangumiSubjectCollectionType
import com.wynime.datasources.bangumi.models.BangumiSubjectRevision
import com.wynime.datasources.bangumi.models.BangumiSubjectType
import com.wynime.datasources.bangumi.models.BangumiUser
import com.wynime.datasources.bangumi.models.BangumiUserEpisodeCollection
import com.wynime.datasources.bangumi.models.BangumiUserSubjectCollection
import com.wynime.datasources.bangumi.models.BangumiUserSubjectCollectionModifyPayload
import com.wynime.datasources.bangumi.models.BangumiV0RelatedSubject
import com.wynime.datasources.bangumi.models.BangumiV0SubjectRelation

import com.wynime.datasources.bangumi.infrastructure.*
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.request.forms.formData
import io.ktor.client.engine.HttpClientEngine
import kotlinx.serialization.json.Json
import io.ktor.http.ParametersBuilder
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

open class DefaultApi : ApiClient {

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

    open suspend fun addSubjectToIndexByIndexId(indexId: kotlin.Int, bangumiIndexSubjectAddInfo: BangumiIndexSubjectAddInfo? = null): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>("HTTPBearer")

        val localVariableBody = bangumiIndexSubjectAddInfo

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.POST,
            "/v0/indices/{index_id}/subjects".replace("{" + "index_id" + "}", "$indexId"),
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

    open suspend fun collectIndexByIndexIdAndUserId(indexId: kotlin.Int): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>("HTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.POST,
            "/v0/indices/{index_id}/collect".replace("{" + "index_id" + "}", "$indexId"),
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

    open suspend fun delelteSubjectFromIndexByIndexIdAndSubjectID(indexId: kotlin.Int, subjectId: kotlin.Int): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>("HTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.DELETE,
            "/v0/indices/{index_id}/subjects/{subject_id}".replace("{" + "index_id" + "}", "$indexId").replace("{" + "subject_id" + "}", "$subjectId"),
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
    open suspend fun editIndexById(indexId: kotlin.Int, bangumiIndexBasicInfo: BangumiIndexBasicInfo? = null): HttpResponse<BangumiIndex> {

        val localVariableAuthNames = listOf<String>("HTTPBearer")

        val localVariableBody = bangumiIndexBasicInfo

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.PUT,
            "/v0/indices/{index_id}".replace("{" + "index_id" + "}", "$indexId"),
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

    open suspend fun editIndexSubjectsByIndexIdAndSubjectID(indexId: kotlin.Int, subjectId: kotlin.Int, bangumiIndexSubjectEditInfo: BangumiIndexSubjectEditInfo? = null): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>("HTTPBearer")

        val localVariableBody = bangumiIndexSubjectEditInfo

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.PUT,
            "/v0/indices/{index_id}/subjects/{subject_id}".replace("{" + "index_id" + "}", "$indexId").replace("{" + "subject_id" + "}", "$subjectId"),
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
    open suspend fun getCharacterById(characterId: kotlin.Int): HttpResponse<BangumiCharacterDetail> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/characters/{character_id}".replace("{" + "character_id" + "}", "$characterId"),
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

    open suspend fun getCharacterImageById(characterId: kotlin.Int, type: kotlin.String): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>("OptionalHTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        type?.apply { localVariableQuery["type"] = listOf("$type") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/characters/{character_id}/image".replace("{" + "character_id" + "}", "$characterId"),
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
    open suspend fun getCharacterRevisionByRevisionId(revisionId: kotlin.Int): HttpResponse<BangumiCharacterRevision> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/revisions/characters/{revision_id}".replace("{" + "revision_id" + "}", "$revisionId"),
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
    open suspend fun getCharacterRevisions(characterId: kotlin.Int, limit: kotlin.Int? = 30, offset: kotlin.Int? = 0): HttpResponse<BangumiPagedRevision> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        characterId?.apply { localVariableQuery["character_id"] = listOf("$characterId") }
        limit?.apply { localVariableQuery["limit"] = listOf("$limit") }
        offset?.apply { localVariableQuery["offset"] = listOf("$offset") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/revisions/characters",
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
    open suspend fun getEpisodeById(episodeId: kotlin.Int): HttpResponse<BangumiEpisodeDetail> {

        val localVariableAuthNames = listOf<String>("OptionalHTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/episodes/{episode_id}".replace("{" + "episode_id" + "}", "$episodeId"),
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
    open suspend fun getEpisodeRevisionByRevisionId(revisionId: kotlin.Int): HttpResponse<BangumiDetailedRevision> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/revisions/episodes/{revision_id}".replace("{" + "revision_id" + "}", "$revisionId"),
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
    open suspend fun getEpisodeRevisions(episodeId: kotlin.Int, limit: kotlin.Int? = 30, offset: kotlin.Int? = 0): HttpResponse<BangumiPagedRevision> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        episodeId?.apply { localVariableQuery["episode_id"] = listOf("$episodeId") }
        limit?.apply { localVariableQuery["limit"] = listOf("$limit") }
        offset?.apply { localVariableQuery["offset"] = listOf("$offset") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/revisions/episodes",
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
    open suspend fun getEpisodes(subjectId: kotlin.Int, type: BangumiEpType? = null, limit: kotlin.Int? = 100, offset: kotlin.Int? = 0): HttpResponse<BangumiPagedEpisode> {

        val localVariableAuthNames = listOf<String>("OptionalHTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        subjectId?.apply { localVariableQuery["subject_id"] = listOf("$subjectId") }
        type?.apply { localVariableQuery["type"] = listOf("${ type.value }") }
        limit?.apply { localVariableQuery["limit"] = listOf("$limit") }
        offset?.apply { localVariableQuery["offset"] = listOf("$offset") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/episodes",
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
    open suspend fun getIndexById(indexId: kotlin.Int): HttpResponse<BangumiIndex> {

        val localVariableAuthNames = listOf<String>("OptionalHTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/indices/{index_id}".replace("{" + "index_id" + "}", "$indexId"),
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

    open suspend fun getIndexSubjectsByIndexId(indexId: kotlin.Int, type: BangumiSubjectType? = null, limit: kotlin.Int? = 30, offset: kotlin.Int? = 0): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>("OptionalHTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        type?.apply { localVariableQuery["type"] = listOf("${ type.value }") }
        limit?.apply { localVariableQuery["limit"] = listOf("$limit") }
        offset?.apply { localVariableQuery["offset"] = listOf("$offset") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/indices/{index_id}/subjects".replace("{" + "index_id" + "}", "$indexId"),
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
    open suspend fun getMyself(): HttpResponse<BangumiUser> {

        val localVariableAuthNames = listOf<String>("HTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/me",
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
    open suspend fun getPersonById(personId: kotlin.Int): HttpResponse<BangumiPersonDetail> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/persons/{person_id}".replace("{" + "person_id" + "}", "$personId"),
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

    open suspend fun getPersonImageById(personId: kotlin.Int, type: kotlin.String): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>("OptionalHTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        type?.apply { localVariableQuery["type"] = listOf("$type") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/persons/{person_id}/image".replace("{" + "person_id" + "}", "$personId"),
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
    open suspend fun getPersonRevisionByRevisionId(revisionId: kotlin.Int): HttpResponse<BangumiPersonRevision> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/revisions/persons/{revision_id}".replace("{" + "revision_id" + "}", "$revisionId"),
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
    open suspend fun getPersonRevisions(personId: kotlin.Int, limit: kotlin.Int? = 30, offset: kotlin.Int? = 0): HttpResponse<BangumiPagedRevision> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        personId?.apply { localVariableQuery["person_id"] = listOf("$personId") }
        limit?.apply { localVariableQuery["limit"] = listOf("$limit") }
        offset?.apply { localVariableQuery["offset"] = listOf("$offset") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/revisions/persons",
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
    open suspend fun getRelatedCharactersByPersonId(personId: kotlin.Int): HttpResponse<kotlin.collections.List<BangumiPersonCharacter>> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/persons/{person_id}/characters".replace("{" + "person_id" + "}", "$personId"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = false,
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap<GetRelatedCharactersByPersonIdResponse>().map { value }
    }

    @Serializable(GetRelatedCharactersByPersonIdResponse.Companion::class)
    private class GetRelatedCharactersByPersonIdResponse(val value: List<BangumiPersonCharacter>) {
        companion object : KSerializer<GetRelatedCharactersByPersonIdResponse> {
            private val serializer: KSerializer<List<BangumiPersonCharacter>> = serializer<List<BangumiPersonCharacter>>()
            override val descriptor = serializer.descriptor
            override fun serialize(encoder: Encoder, value: GetRelatedCharactersByPersonIdResponse) = serializer.serialize(encoder, value.value)
            override fun deserialize(decoder: Decoder) = GetRelatedCharactersByPersonIdResponse(serializer.deserialize(decoder))
        }
    }

    @Suppress("UNCHECKED_CAST")
    open suspend fun getRelatedCharactersBySubjectId(subjectId: kotlin.Int): HttpResponse<kotlin.collections.List<BangumiRelatedCharacter>> {

        val localVariableAuthNames = listOf<String>("OptionalHTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/subjects/{subject_id}/characters".replace("{" + "subject_id" + "}", "$subjectId"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap<GetRelatedCharactersBySubjectIdResponse>().map { value }
    }

    @Serializable(GetRelatedCharactersBySubjectIdResponse.Companion::class)
    private class GetRelatedCharactersBySubjectIdResponse(val value: List<BangumiRelatedCharacter>) {
        companion object : KSerializer<GetRelatedCharactersBySubjectIdResponse> {
            private val serializer: KSerializer<List<BangumiRelatedCharacter>> = serializer<List<BangumiRelatedCharacter>>()
            override val descriptor = serializer.descriptor
            override fun serialize(encoder: Encoder, value: GetRelatedCharactersBySubjectIdResponse) = serializer.serialize(encoder, value.value)
            override fun deserialize(decoder: Decoder) = GetRelatedCharactersBySubjectIdResponse(serializer.deserialize(decoder))
        }
    }

    @Suppress("UNCHECKED_CAST")
    open suspend fun getRelatedPersonsByCharacterId(characterId: kotlin.Int): HttpResponse<kotlin.collections.List<BangumiCharacterPerson>> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/characters/{character_id}/persons".replace("{" + "character_id" + "}", "$characterId"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = false,
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap<GetRelatedPersonsByCharacterIdResponse>().map { value }
    }

    @Serializable(GetRelatedPersonsByCharacterIdResponse.Companion::class)
    private class GetRelatedPersonsByCharacterIdResponse(val value: List<BangumiCharacterPerson>) {
        companion object : KSerializer<GetRelatedPersonsByCharacterIdResponse> {
            private val serializer: KSerializer<List<BangumiCharacterPerson>> = serializer<List<BangumiCharacterPerson>>()
            override val descriptor = serializer.descriptor
            override fun serialize(encoder: Encoder, value: GetRelatedPersonsByCharacterIdResponse) = serializer.serialize(encoder, value.value)
            override fun deserialize(decoder: Decoder) = GetRelatedPersonsByCharacterIdResponse(serializer.deserialize(decoder))
        }
    }

    @Suppress("UNCHECKED_CAST")
    open suspend fun getRelatedPersonsBySubjectId(subjectId: kotlin.Int): HttpResponse<kotlin.collections.List<BangumiRelatedPerson>> {

        val localVariableAuthNames = listOf<String>("OptionalHTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/subjects/{subject_id}/persons".replace("{" + "subject_id" + "}", "$subjectId"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap<GetRelatedPersonsBySubjectIdResponse>().map { value }
    }

    @Serializable(GetRelatedPersonsBySubjectIdResponse.Companion::class)
    private class GetRelatedPersonsBySubjectIdResponse(val value: List<BangumiRelatedPerson>) {
        companion object : KSerializer<GetRelatedPersonsBySubjectIdResponse> {
            private val serializer: KSerializer<List<BangumiRelatedPerson>> = serializer<List<BangumiRelatedPerson>>()
            override val descriptor = serializer.descriptor
            override fun serialize(encoder: Encoder, value: GetRelatedPersonsBySubjectIdResponse) = serializer.serialize(encoder, value.value)
            override fun deserialize(decoder: Decoder) = GetRelatedPersonsBySubjectIdResponse(serializer.deserialize(decoder))
        }
    }

    @Suppress("UNCHECKED_CAST")
    open suspend fun getRelatedSubjectsByCharacterId(characterId: kotlin.Int): HttpResponse<kotlin.collections.List<BangumiV0RelatedSubject>> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/characters/{character_id}/subjects".replace("{" + "character_id" + "}", "$characterId"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = false,
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap<GetRelatedSubjectsByCharacterIdResponse>().map { value }
    }

    @Serializable(GetRelatedSubjectsByCharacterIdResponse.Companion::class)
    private class GetRelatedSubjectsByCharacterIdResponse(val value: List<BangumiV0RelatedSubject>) {
        companion object : KSerializer<GetRelatedSubjectsByCharacterIdResponse> {
            private val serializer: KSerializer<List<BangumiV0RelatedSubject>> = serializer<List<BangumiV0RelatedSubject>>()
            override val descriptor = serializer.descriptor
            override fun serialize(encoder: Encoder, value: GetRelatedSubjectsByCharacterIdResponse) = serializer.serialize(encoder, value.value)
            override fun deserialize(decoder: Decoder) = GetRelatedSubjectsByCharacterIdResponse(serializer.deserialize(decoder))
        }
    }

    @Suppress("UNCHECKED_CAST")
    open suspend fun getRelatedSubjectsByPersonId(personId: kotlin.Int): HttpResponse<kotlin.collections.List<BangumiV0RelatedSubject>> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/persons/{person_id}/subjects".replace("{" + "person_id" + "}", "$personId"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = false,
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap<GetRelatedSubjectsByPersonIdResponse>().map { value }
    }

    @Serializable(GetRelatedSubjectsByPersonIdResponse.Companion::class)
    private class GetRelatedSubjectsByPersonIdResponse(val value: List<BangumiV0RelatedSubject>) {
        companion object : KSerializer<GetRelatedSubjectsByPersonIdResponse> {
            private val serializer: KSerializer<List<BangumiV0RelatedSubject>> = serializer<List<BangumiV0RelatedSubject>>()
            override val descriptor = serializer.descriptor
            override fun serialize(encoder: Encoder, value: GetRelatedSubjectsByPersonIdResponse) = serializer.serialize(encoder, value.value)
            override fun deserialize(decoder: Decoder) = GetRelatedSubjectsByPersonIdResponse(serializer.deserialize(decoder))
        }
    }

    @Suppress("UNCHECKED_CAST")
    open suspend fun getRelatedSubjectsBySubjectId(subjectId: kotlin.Int): HttpResponse<kotlin.collections.List<BangumiV0SubjectRelation>> {

        val localVariableAuthNames = listOf<String>("OptionalHTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/subjects/{subject_id}/subjects".replace("{" + "subject_id" + "}", "$subjectId"),
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true,
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap<GetRelatedSubjectsBySubjectIdResponse>().map { value }
    }

    @Serializable(GetRelatedSubjectsBySubjectIdResponse.Companion::class)
    private class GetRelatedSubjectsBySubjectIdResponse(val value: List<BangumiV0SubjectRelation>) {
        companion object : KSerializer<GetRelatedSubjectsBySubjectIdResponse> {
            private val serializer: KSerializer<List<BangumiV0SubjectRelation>> = serializer<List<BangumiV0SubjectRelation>>()
            override val descriptor = serializer.descriptor
            override fun serialize(encoder: Encoder, value: GetRelatedSubjectsBySubjectIdResponse) = serializer.serialize(encoder, value.value)
            override fun deserialize(decoder: Decoder) = GetRelatedSubjectsBySubjectIdResponse(serializer.deserialize(decoder))
        }
    }

    @Suppress("UNCHECKED_CAST")
    open suspend fun getSubjectById(subjectId: kotlin.Int): HttpResponse<BangumiSubject> {

        val localVariableAuthNames = listOf<String>("OptionalHTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/subjects/{subject_id}".replace("{" + "subject_id" + "}", "$subjectId"),
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

    open suspend fun getSubjectImageById(subjectId: kotlin.Int, type: kotlin.String): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>("OptionalHTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        type?.apply { localVariableQuery["type"] = listOf("$type") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/subjects/{subject_id}/image".replace("{" + "subject_id" + "}", "$subjectId"),
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
    open suspend fun getSubjectRevisionByRevisionId(revisionId: kotlin.Int): HttpResponse<BangumiSubjectRevision> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/revisions/subjects/{revision_id}".replace("{" + "revision_id" + "}", "$revisionId"),
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
    open suspend fun getSubjectRevisions(subjectId: kotlin.Int, limit: kotlin.Int? = 30, offset: kotlin.Int? = 0): HttpResponse<BangumiPagedRevision> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        subjectId?.apply { localVariableQuery["subject_id"] = listOf("$subjectId") }
        limit?.apply { localVariableQuery["limit"] = listOf("$limit") }
        offset?.apply { localVariableQuery["offset"] = listOf("$offset") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/revisions/subjects",
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

    open suspend fun getUserAvatarByName(username: kotlin.String, type: kotlin.String): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        type?.apply { localVariableQuery["type"] = listOf("$type") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/users/{username}/avatar".replace("{" + "username" + "}", "$username"),
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
    open suspend fun getUserByName(username: kotlin.String): HttpResponse<BangumiUser> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/users/{username}".replace("{" + "username" + "}", "$username"),
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
    open suspend fun getUserCollection(username: kotlin.String, subjectId: kotlin.Int): HttpResponse<BangumiUserSubjectCollection> {

        val localVariableAuthNames = listOf<String>("OptionalHTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/users/{username}/collections/{subject_id}".replace("{" + "username" + "}", "$username").replace("{" + "subject_id" + "}", "$subjectId"),
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
    open suspend fun getUserCollectionsByUsername(username: kotlin.String, subjectType: BangumiSubjectType? = null, type: BangumiSubjectCollectionType? = null, limit: kotlin.Int? = 30, offset: kotlin.Int? = 0): HttpResponse<BangumiPagedUserCollection> {

        val localVariableAuthNames = listOf<String>("OptionalHTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        subjectType?.apply { localVariableQuery["subject_type"] = listOf("${ subjectType.value }") }
        type?.apply { localVariableQuery["type"] = listOf("${ type.value }") }
        limit?.apply { localVariableQuery["limit"] = listOf("$limit") }
        offset?.apply { localVariableQuery["offset"] = listOf("$offset") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/users/{username}/collections".replace("{" + "username" + "}", "$username"),
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
    open suspend fun getUserEpisodeCollection(episodeId: kotlin.Int): HttpResponse<BangumiUserEpisodeCollection> {

        val localVariableAuthNames = listOf<String>("HTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/users/-/collections/-/episodes/{episode_id}".replace("{" + "episode_id" + "}", "$episodeId"),
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
    open suspend fun getUserSubjectEpisodeCollection(subjectId: kotlin.Int, offset: kotlin.Int? = 0, limit: kotlin.Int? = 100, episodeType: BangumiEpType? = null): HttpResponse<BangumiGetUserSubjectEpisodeCollection200Response> {

        val localVariableAuthNames = listOf<String>("HTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        offset?.apply { localVariableQuery["offset"] = listOf("$offset") }
        limit?.apply { localVariableQuery["limit"] = listOf("$limit") }
        episodeType?.apply { localVariableQuery["episode_type"] = listOf("${ episodeType.value }") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/v0/users/-/collections/{subject_id}/episodes".replace("{" + "subject_id" + "}", "$subjectId"),
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
    open suspend fun newIndex(): HttpResponse<BangumiIndex> {

        val localVariableAuthNames = listOf<String>("HTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.POST,
            "/v0/indices",
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

    open suspend fun patchUserCollection(subjectId: kotlin.Int, bangumiUserSubjectCollectionModifyPayload: BangumiUserSubjectCollectionModifyPayload? = null): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>("OptionalHTTPBearer")

        val localVariableBody = bangumiUserSubjectCollectionModifyPayload

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.PATCH,
            "/v0/users/-/collections/{subject_id}".replace("{" + "subject_id" + "}", "$subjectId"),
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

    open suspend fun patchUserSubjectEpisodeCollection(subjectId: kotlin.Int, bangumiPatchUserSubjectEpisodeCollectionRequest: BangumiPatchUserSubjectEpisodeCollectionRequest? = null): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>("HTTPBearer")

        val localVariableBody = bangumiPatchUserSubjectEpisodeCollectionRequest

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.PATCH,
            "/v0/users/-/collections/{subject_id}/episodes".replace("{" + "subject_id" + "}", "$subjectId"),
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

    open suspend fun postUserCollection(subjectId: kotlin.Int, bangumiUserSubjectCollectionModifyPayload: BangumiUserSubjectCollectionModifyPayload? = null): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>("OptionalHTTPBearer")

        val localVariableBody = bangumiUserSubjectCollectionModifyPayload

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.POST,
            "/v0/users/-/collections/{subject_id}".replace("{" + "subject_id" + "}", "$subjectId"),
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

    open suspend fun putUserEpisodeCollection(episodeId: kotlin.Int, bangumiPutUserEpisodeCollectionRequest: BangumiPutUserEpisodeCollectionRequest? = null): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>("HTTPBearer")

        val localVariableBody = bangumiPutUserEpisodeCollectionRequest

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.PUT,
            "/v0/users/-/collections/-/episodes/{episode_id}".replace("{" + "episode_id" + "}", "$episodeId"),
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
    open suspend fun searchSubjects(limit: kotlin.Int? = null, offset: kotlin.Int? = null, bangumiSearchSubjectsRequest: BangumiSearchSubjectsRequest? = null): HttpResponse<BangumiSearchSubjects200Response> {

        val localVariableAuthNames = listOf<String>()

        val localVariableBody = bangumiSearchSubjectsRequest

        val localVariableQuery = mutableMapOf<String, List<String>>()
        limit?.apply { localVariableQuery["limit"] = listOf("$limit") }
        offset?.apply { localVariableQuery["offset"] = listOf("$offset") }
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.POST,
            "/v0/search/subjects",
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

    open suspend fun uncollectIndexByIndexIdAndUserId(indexId: kotlin.Int): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>("HTTPBearer")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.DELETE,
            "/v0/indices/{index_id}/collect".replace("{" + "index_id" + "}", "$indexId"),
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
