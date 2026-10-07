package com.wynime.app.data.persistent.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.wynime.app.data.persistent.database.dao.SubjectCollectionEntity

@Entity(
    "subject_review",
    indices = [
        Index(value = ["subjectId"], orders = [Index.Order.ASC]),
        Index(value = ["updatedAt"], orders = [Index.Order.DESC], name = "index_updatedAt_desc"),
        Index(value = ["updatedAt"], orders = [Index.Order.ASC], name = "index_updatedAt_asc"),
        Index(value = ["rating"], orders = [Index.Order.DESC], name = "index_rating_desc"),
        Index(value = ["rating"], orders = [Index.Order.ASC], name = "index_rating_asc"),
    ],
    primaryKeys = ["subjectId", "authorId"],
    foreignKeys = [
        ForeignKey(
            SubjectCollectionEntity::class,
            parentColumns = ["subjectId"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SubjectReviewEntity(
    val subjectId: Int,
    val authorId: Int,

    val authorNickname: String,
    val authorAvatarUrl: String?,

    val updatedAt: Long,
    val rating: Int,
    val content: String,
)
