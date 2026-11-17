package com.wynime.app.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.wynime.app.data.models.UserInfo

@Serializable
internal data class BangumiSubjectTopicPage(val data: List<BangumiSubjectTopic>, val total: Int)

@Serializable
internal data class BangumiSubjectTopic(
    val id: Int,
    val title: String,
    @SerialName("creatorID") val creatorId: Int,
    val createdAt: Long,
    val creator: BangumiTopicCreator? = null,
)

@Serializable
internal data class BangumiSubjectTopicDetail(
    val content: String? = null,
    val creator: BangumiTopicCreator? = null,
    val replies: List<BangumiTopicPost> = emptyList(),
) {
    fun openingPost(topic: BangumiSubjectTopic): String? = content?.takeIf(String::isNotBlank)
        ?: replies.firstOrNull()
            ?.takeIf { it.creatorId == topic.creatorId && it.createdAt == topic.createdAt && it.state == 0 }
            ?.content?.takeIf(String::isNotBlank)
}

@Serializable
internal data class BangumiTopicPost(
    @SerialName("creatorID") val creatorId: Int,
    val createdAt: Long,
    val state: Int = 0,
    val content: String? = null,
)

@Serializable
internal data class BangumiTopicCreator(
    val id: Int,
    val username: String? = null,
    val nickname: String? = null,
    val avatar: BangumiTopicAvatar? = null,
) {
    fun toUserInfo() = UserInfo(id.toString(), username, nickname, avatar?.medium ?: avatar?.large)
}

@Serializable
internal data class BangumiTopicAvatar(val medium: String? = null, val large: String? = null)
