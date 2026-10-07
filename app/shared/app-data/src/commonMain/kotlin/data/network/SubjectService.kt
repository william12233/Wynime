package com.wynime.app.data.network

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.bangumi.BangumiSyncState
import com.wynime.app.data.models.subject.CharacterInfo
import com.wynime.app.data.models.subject.CharacterRole
import com.wynime.app.data.models.subject.PersonInfo
import com.wynime.app.data.models.subject.PersonPosition
import com.wynime.app.data.models.subject.PersonType
import com.wynime.app.data.models.subject.RatingCounts
import com.wynime.app.data.models.subject.RelatedCharacterInfo
import com.wynime.app.data.models.subject.RelatedPersonInfo
import com.wynime.app.data.models.subject.SelfRatingInfo
import com.wynime.app.data.models.subject.SubjectCollectionCounts
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.models.CollectionTypeDto
import com.wynime.models.PersonDto
import com.wynime.models.SubjectCollectionDto
import com.wynime.models.SubjectRecommendationDto
import com.wynime.models.UpdateSubjectCollectionRequestDto
import com.wynime.datasources.bangumi.models.BangumiCount
import com.wynime.datasources.bangumi.models.BangumiSubjectCollectionType
import com.wynime.datasources.bangumi.models.BangumiUserSubjectCollection
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.coroutines.flows.FlowRestarter
import com.wynime.utils.coroutines.flows.restartable
import com.wynime.utils.logging.logger
import kotlin.coroutines.CoroutineContext

interface SubjectService {
    suspend fun getSubjectCollections(
        type: BangumiSubjectCollectionType?,
        offset: Int,
        limit: Int
    ): List<SubjectCollectionDto>

    suspend fun getSubjectCollectionsPage(
        type: BangumiSubjectCollectionType?,
        offset: Int,
        limit: Int,
        onItemHydrated: suspend (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): SubjectCollectionPage {
        return SubjectCollectionPage(
            items = getSubjectCollections(type, offset, limit),
            offset = offset,
            requestedLimit = limit,
            total = null,
        )
    }

    suspend fun getSubjectCollection(subjectId: Int): SubjectCollectionDto?

    suspend fun getSubjectRelations(
        subjectId: Int,
        withCharacterActors: Boolean,
    ): BatchSubjectRelations

    fun subjectCollectionById(subjectId: Int): Flow<SubjectCollectionDto?>

    suspend fun patchSubjectCollection(subjectId: Int, payload: UpdateSubjectCollectionRequestDto)

    suspend fun deleteSubjectCollection(subjectId: Int) {
        throw RepositoryRequestError("Subject collection deletion is not available")
    }

    suspend fun getSubjectRecommendations(subjectId: Int, limit: Int): List<SubjectRecommendationDto>

    fun subjectCollectionCountsFlow(): Flow<SubjectCollectionCounts>

    suspend fun performBangumiFullSync()

    suspend fun getBangumiFullSyncState(): BangumiSyncState?
}

data class SubjectCollectionPage(
    val items: List<SubjectCollectionDto>,
    val offset: Int,
    val requestedLimit: Int,
    val total: Int?,
    val omittedSubjectIds: List<Int> = emptyList(),

    val sourceItemCount: Int = items.size,
)

data class BatchSubjectCollection(
    val batchSubjectDetails: BatchSubjectDetails,

    val collection: BangumiUserSubjectCollection?,
)

private fun BangumiCount.toRatingCounts() = RatingCounts(
    _1 ?: 0,
    _2 ?: 0,
    _3 ?: 0,
    _4 ?: 0,
    _5 ?: 0,
    _6 ?: 0,
    _7 ?: 0,
    _8 ?: 0,
    _9 ?: 0,
    _10 ?: 0,
)

data class BatchSubjectDetails(
    val subjectInfo: SubjectInfo,
    val mainEpisodeCount: Int,
    val lightSubjectRelations: LightSubjectRelations,
)

data class LightSubjectRelations(
    val lightRelatedPersonInfoList: List<LightRelatedPersonInfo>,
    val lightRelatedCharacterInfoList: List<LightRelatedCharacterInfo>,
)

data class LightRelatedPersonInfo(
    val name: String,
    val position: PersonPosition,
)

data class LightRelatedCharacterInfo(
    val id: Int,
    val name: String,
    val nameCn: String,
    val role: CharacterRole,
)

data class BatchSubjectRelations(
    val subjectId: Int,
    val relatedCharacterInfoList: List<RelatedCharacterInfo>,
    val relatedPersonInfoList: List<RelatedPersonInfo>,
) {
    val allPersons
        get() = relatedCharacterInfoList.asSequence()
            .flatMap { it.character.actors } + relatedPersonInfoList.asSequence().map { it.personInfo }
}

internal fun BangumiUserSubjectCollection?.toSelfRatingInfo(): SelfRatingInfo {
    if (this == null) {
        return SelfRatingInfo.Empty
    }
    return SelfRatingInfo(
        score = rate,
        comment = comment.takeUnless { it.isNullOrBlank() },
        tags = tags,
        isPrivate = private,
    )
}

private fun BangumiSubjectCollectionType.toWynimeCollectionType(): CollectionTypeDto {
    return when (this) {
        BangumiSubjectCollectionType.Wish -> CollectionTypeDto.WISH
        BangumiSubjectCollectionType.Done -> CollectionTypeDto.DONE
        BangumiSubjectCollectionType.Doing -> CollectionTypeDto.DOING
        BangumiSubjectCollectionType.OnHold -> CollectionTypeDto.ON_HOLD
        BangumiSubjectCollectionType.Dropped -> CollectionTypeDto.DROPPED
    }
}

private fun PersonDto.toPersonInfo(): PersonInfo {
    return PersonInfo(
        id = id.toInt(),
        name = name,
        type = PersonType.fromId(type),
        careers = emptyList(),
        imageLarge = imageLarge,
        imageMedium = imageMedium,
        summary = summary,
        locked = false,
        nameCn = nameCn,
    )
}
