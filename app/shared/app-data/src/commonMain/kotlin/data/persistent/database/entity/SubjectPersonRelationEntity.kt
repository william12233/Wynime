package com.wynime.app.data.persistent.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.wynime.app.data.models.subject.PersonPosition
import com.wynime.app.data.persistent.database.dao.SubjectCollectionEntity

@Entity(
    "subject_person",
    indices = [
        Index(value = ["personId"], orders = [Index.Order.ASC]),
    ],
    primaryKeys = ["subjectId", "personId"],
    foreignKeys = [
        ForeignKey(
            SubjectCollectionEntity::class,
            parentColumns = ["subjectId"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
        ForeignKey(
            PersonEntity::class,
            parentColumns = ["personId"],
            childColumns = ["personId"],
            onDelete = ForeignKey.RESTRICT,
            deferred = true,
        ),
    ],
)
data class SubjectPersonRelationEntity(
    val subjectId: Int,
    val index: Int,
    val personId: Int,
    val position: PersonPosition,
)