package com.wynime.app.data.persistent.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.wynime.app.data.models.subject.CharacterRole
import com.wynime.app.data.persistent.database.dao.SubjectCollectionEntity

@Entity(
    "subject_character",
    indices = [

        Index(value = ["subjectId"], orders = [Index.Order.ASC]),
        Index(value = ["characterId"], orders = [Index.Order.ASC]),
    ],
    primaryKeys = ["subjectId", "characterId"],
    foreignKeys = [
        ForeignKey(
            SubjectCollectionEntity::class,
            parentColumns = ["subjectId"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
        ForeignKey(
            CharacterEntity::class,
            parentColumns = ["characterId"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.RESTRICT,
            deferred = true,
        ),
    ],
)
data class SubjectCharacterRelationEntity(
    val subjectId: Int,
    val index: Int,
    val characterId: Int,
    val role: CharacterRole,
)