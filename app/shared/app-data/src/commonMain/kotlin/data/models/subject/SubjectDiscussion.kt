package com.wynime.app.data.models.subject

import com.wynime.app.data.models.UserInfo

data class SubjectDiscussion(
    val id: Int,
    val title: String,
    val author: UserInfo,
    val createdAtMillis: Long,
    val content: String?,
) {
    val originalUrl: String get() = "https://bgm.tv/subject/topic/$id"
}
