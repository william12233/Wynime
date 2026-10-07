package com.wynime.app.data.repository.subject

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import com.wynime.app.data.models.subject.CharacterInfo
import com.wynime.app.data.models.subject.PersonInfo
import com.wynime.app.data.models.subject.RelatedCharacterInfo
import com.wynime.app.data.models.subject.RelatedPersonInfo
import com.wynime.app.data.models.subject.SubjectCollectionInfo
import com.wynime.app.data.models.subject.SubjectSeriesInfo
import com.wynime.app.data.network.WynimeSubjectRelationIndexService
import com.wynime.app.data.network.BatchSubjectRelations
import com.wynime.app.data.network.SubjectService
import com.wynime.app.data.persistent.database.dao.RelatedCharacterView
import com.wynime.app.data.persistent.database.dao.RelatedPersonView
import com.wynime.app.data.persistent.database.dao.SubjectCollectionDao
import com.wynime.app.data.persistent.database.dao.SubjectRelationsDao
import com.wynime.app.data.persistent.database.entity.CharacterActorEntity
import com.wynime.app.data.persistent.database.entity.CharacterEntity
import com.wynime.app.data.persistent.database.entity.PersonEntity
import com.wynime.app.data.persistent.database.entity.SubjectCharacterRelationEntity
import com.wynime.app.data.persistent.database.entity.SubjectPersonRelationEntity
import com.wynime.app.data.repository.Repository
import com.wynime.app.data.repository.RepositoryServiceUnavailableException
import com.wynime.utils.platform.collections.mapToIntArray
import com.wynime.utils.platform.currentTimeMillis
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

sealed class SubjectRelationsRepository(
    defaultDispatcher: CoroutineContext = Dispatchers.Default
) : Repository(defaultDispatcher) {

    abstract fun subjectSequelSubjectIdsFlow(subjectId: Int): Flow<List<Int>>

    abstract fun subjectSequelSubjectsFlow(subjectId: Int): Flow<List<SubjectCollectionInfo>>

    abstract fun subjectSeriesInfoFlow(subjectId: Int): Flow<SubjectSeriesInfo>

    abstract fun subjectRelatedPersonsFlow(subjectId: Int): Flow<List<RelatedPersonInfo>>
    abstract fun subjectRelatedCharactersFlow(subjectId: Int): Flow<List<RelatedCharacterInfo>>
}

class DefaultSubjectRelationsRepository(
    private val subjectCollectionDao: SubjectCollectionDao,
    private val subjectRelationsDao: SubjectRelationsDao,
    private val subjectService: SubjectService,
    private val subjectCollectionRepository: SubjectCollectionRepository,
    private val wynimeSubjectRelationIndexService: WynimeSubjectRelationIndexService,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
    private val autoRefreshPeriod: Duration = 1.hours,

    private val cacheExpiry: Duration = 3.days,
) : SubjectRelationsRepository(defaultDispatcher) {
    override fun subjectSequelSubjectIdsFlow(subjectId: Int): Flow<List<Int>> = flow {
        emit(
            kotlinx.coroutines.withTimeoutOrNull(10_000) {

                wynimeSubjectRelationIndexService.getSubjectRelationIndex(subjectId).sequelSubjects
            }
                ?: throw RepositoryServiceUnavailableException("Failed to fetch subject sequel subjects for $subjectId due to timeout"),
        )
    }.flowOn(defaultDispatcher)

    override fun subjectSequelSubjectsFlow(subjectId: Int): Flow<List<SubjectCollectionInfo>> {

        return subjectSequelSubjectIdsFlow(subjectId)
            .flatMapLatest { list ->
                if (list.isEmpty()) {
                    return@flatMapLatest flowOf(emptyList())
                }
                combine(
                    list.map { relatedSubjectId ->
                        subjectCollectionRepository.subjectCollectionFlow(relatedSubjectId)
                    },
                ) {
                    it.toList()
                }
            }.flowOn(defaultDispatcher)
    }

    override fun subjectSeriesInfoFlow(subjectId: Int): Flow<SubjectSeriesInfo> = flow {
        emit(
            wynimeSubjectRelationIndexService.getSubjectRelationIndex(subjectId),
        )
    }.combine(subjectCollectionRepository.subjectCollectionFlow(subjectId)) { relations, requestingSubject ->
        combine(
            (relations.sequelSubjects.toSet() + relations.seriesMainSubjectIds).map {
                subjectCollectionRepository.subjectCollectionFlow(it)
            },
        ) { subjectCollectionInfos ->
            SubjectSeriesInfo.compute(
                requestingSubject = requestingSubject,
            )
        }
    }.flatMapLatest {
        it
    }.flowOn(defaultDispatcher)

    override fun subjectRelatedPersonsFlow(subjectId: Int): Flow<List<RelatedPersonInfo>> {
        return subjectCollectionRepository.subjectCollectionFlow(subjectId)
            .autoRefresh()
            .flatMapLatest { subjectCollection ->
                if ((currentTimeMillis() - subjectCollection.cachedCharactersUpdated).milliseconds > cacheExpiry) {
                    fetchAndSaveSubjectRelations(subjectId)
                }

                subjectRelationsDao.subjectRelatedPersonsFlow(subjectId).map { list ->
                    list.mapTo(ArrayList(list.size)) {
                        it.toRelatedPersonInfo()
                    }.apply {
                        sortWith(RelatedPersonInfo.ImportanceOrder)
                    }
                }
            }.flowOn(defaultDispatcher)
    }

    override fun subjectRelatedCharactersFlow(subjectId: Int): Flow<List<RelatedCharacterInfo>> {
        return subjectCollectionRepository.subjectCollectionFlow(subjectId)
            .autoRefresh()
            .flatMapLatest { subjectCollection ->
                if ((currentTimeMillis() - subjectCollection.cachedCharactersUpdated).milliseconds > cacheExpiry) {
                    fetchAndSaveSubjectRelations(subjectId)
                }

                subjectRelationsDao.subjectRelatedCharactersFlow(subjectId).flatMapLatest { list ->
                    subjectRelationsDao.characterActorsFlow(list.mapToIntArray { it.character.characterId })
                        .map { actors ->
                            list.mapTo(ArrayList(list.size)) { relatedCharacterView ->
                                val characterId = relatedCharacterView.character.characterId
                                relatedCharacterView.toRelatedCharacterInfo(
                                    actors = actors
                                        .asSequence()
                                        .filter { it.characterId == characterId }
                                        .map { it.person.toPersonInfo() }
                                        .toList(),
                                )
                            }.apply {
                                sortWith(RelatedCharacterInfo.ImportanceOrder)
                            }
                        }
                }
            }.flowOn(defaultDispatcher)
    }

    private fun <T> Flow<T>.autoRefresh() = refreshTicker().flatMapLatest { this@autoRefresh }

    private fun refreshTicker() = flow {
        while (true) {
            emit(Unit)
            delay(autoRefreshPeriod)
        }
    }

    private suspend fun fetchAndSaveSubjectRelations(subjectId: Int) {
        val batch = subjectService.getSubjectRelations(subjectId, withCharacterActors = true)
        subjectRelationsDao.upsertPersons(batch.allPersons.map { it.toEntity() }.toList())
        subjectRelationsDao.upsertCharacters(batch.relatedCharacterInfoList.map { it.character.toEntity() })
        subjectRelationsDao.upsertCharacterActors(batch.characterActorRelations().toList())

        subjectRelationsDao.upsertSubjectPersonRelations(
            batch.relatedPersonInfoList.map { it.toRelationEntity(subjectId) },
        )
        subjectRelationsDao.upsertSubjectCharacterRelations(
            batch.relatedCharacterInfoList.map { it.toRelationEntity(subjectId) },
        )
        subjectCollectionDao.updateCachedRelationsUpdated(subjectId)
    }

}

private fun RelatedCharacterView.toRelatedCharacterInfo(
    actors: List<PersonInfo>,
): RelatedCharacterInfo {
    return RelatedCharacterInfo(
        index = index,
        character = character.toCharacterInfo(actors),
        role = role,
    )
}

private fun CharacterEntity.toCharacterInfo(actors: List<PersonInfo>): CharacterInfo {
    return CharacterInfo(
        id = characterId,
        name = name,
        nameCn = nameCn,
        actors = actors,
        imageLarge = imageLarge,
        imageMedium = imageMedium,
    )
}

private fun RelatedPersonView.toRelatedPersonInfo(): RelatedPersonInfo {
    return RelatedPersonInfo(
        index = index,
        personInfo = person.toPersonInfo(),
        position = position,
    )
}

private fun PersonEntity.toPersonInfo(): PersonInfo {
    return PersonInfo(
        id = personId,
        name = name,
        type = type,
        careers = emptyList(),
        imageLarge = imageLarge,
        imageMedium = imageMedium,
        summary = summary,
        locked = false,
        nameCn = nameCn,
    )
}

private fun BatchSubjectRelations.characterActorRelations() =
    relatedCharacterInfoList.asSequence().flatMap { relatedCharacterInfo ->
        relatedCharacterInfo.character.actors.asSequence().map { person ->
            CharacterActorEntity(relatedCharacterInfo.character.id, person.id)
        }
    }

private fun CharacterInfo.toEntity(): CharacterEntity {
    return CharacterEntity(
        characterId = id,
        name = name,
        nameCn = nameCn,
        imageLarge = imageLarge,
        imageMedium = imageMedium,
    )
}

private fun PersonInfo.toEntity(): PersonEntity {
    return PersonEntity(
        personId = id,
        name = name,
        nameCn = nameCn,
        type = type,
        imageLarge = imageLarge,
        imageMedium = imageMedium,
        summary = summary,
    )
}

private fun RelatedPersonInfo.toRelationEntity(subjectId: Int): SubjectPersonRelationEntity {
    return SubjectPersonRelationEntity(
        subjectId = subjectId,
        index = index,
        personId = personInfo.id,
        position = position,
    )
}

private fun RelatedCharacterInfo.toRelationEntity(subjectId: Int): SubjectCharacterRelationEntity {
    return SubjectCharacterRelationEntity(
        subjectId = subjectId,
        index = index,
        characterId = character.id,
        role = role,
    )
}
