package com.wynime.app.data.persistent.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.wynime.app.data.persistent.database.dao.EpisodeCollectionEntity

@Entity(
    "episode_comment",
    indices = [
        Index(value = ["episodeId"]),
        Index(value = ["parentCommentId"]),
    ],
    primaryKeys = ["commentId"],
    foreignKeys = [
        ForeignKey(
            EpisodeCollectionEntity::class,
            parentColumns = ["episodeId"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            EpisodeCommentEntity::class,
            parentColumns = ["commentId"],
            childColumns = ["parentCommentId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
)
data class EpisodeCommentEntity(
    val episodeId: Long,
    val commentId: String,
    val authorId: String,

    val parentCommentId: String?,

    val authorNickname: String,
    val authorAvatarUrl: String?,

    val createdAt: Long,
    val content: String,
)

data class EpisodeCommentEntityWithReplies(
    val entity: EpisodeCommentEntity,
    val replies: List<EpisodeCommentEntity>,
)
