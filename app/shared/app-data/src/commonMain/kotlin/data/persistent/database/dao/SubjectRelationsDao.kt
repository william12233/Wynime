package com.wynime.app.data.persistent.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import com.wynime.app.data.models.subject.CharacterRole
import com.wynime.app.data.models.subject.PersonPosition
import com.wynime.app.data.persistent.database.entity.CharacterActorEntity
import com.wynime.app.data.persistent.database.entity.CharacterEntity
import com.wynime.app.data.persistent.database.entity.PersonEntity
import com.wynime.app.data.persistent.database.entity.SubjectCharacterRelationEntity
import com.wynime.app.data.persistent.database.entity.SubjectPersonRelationEntity

@Dao
interface SubjectRelationsDao {
    @Upsert
    suspend fun upsertSubjectCharacterRelations(list: List<SubjectCharacterRelationEntity>)

    @Upsert
    suspend fun upsertSubjectPersonRelations(list: List<SubjectPersonRelationEntity>)

    @Upsert
    suspend fun upsertPersons(list: List<PersonEntity>)

    @Upsert
    suspend fun upsertCharacters(list: List<CharacterEntity>)

    @Upsert
    suspend fun upsertCharacterActors(list: List<CharacterActorEntity>)

    @Query(
        """
        SELECT * FROM person NATURAL JOIN subject_person as s
        WHERE s.subjectId = :subjectId
    """,
    )
    @Transaction
    fun subjectRelatedPersonsFlow(subjectId: Int): Flow<List<RelatedPersonView>>

    @Query(
        """
        SELECT * FROM character NATURAL JOIN subject_character as s
        WHERE s.subjectId = :subjectId
    """,
    )
    @Transaction
    fun subjectRelatedCharactersFlow(subjectId: Int): Flow<List<RelatedCharacterView>>

    @Query(
        """
        SELECT * FROM character_actor JOIN person ON character_actor.actorPersonId = person.personId
        WHERE characterId IN (:characterIds)
    """,
    )
    @Transaction
    fun characterActorsFlow(characterIds: IntArray): Flow<List<CharacterActorView>>
}

data class RelatedPersonView(
    val subjectId: Int,
    val index: Int,
    val position: PersonPosition,

    @Embedded
    val person: PersonEntity,
)

data class RelatedCharacterView(
    val subjectId: Int,
    val index: Int,
    val role: CharacterRole,

    @Embedded
    val character: CharacterEntity,
)

data class CharacterActorView(
    val characterId: Int,
    val actorPersonId: Int,
    @Embedded
    val person: PersonEntity,
)
