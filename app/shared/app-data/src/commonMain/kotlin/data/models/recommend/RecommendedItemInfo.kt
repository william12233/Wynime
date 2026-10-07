package com.wynime.app.data.models.recommend

import com.wynime.app.data.models.subject.TestFollowedSubjectInfos
import com.wynime.app.data.models.subject.preferredDisplayName as subjectPreferredDisplayName
import com.wynime.app.data.models.subject.subjectInfo
import com.wynime.utils.platform.annotations.TestOnly
import kotlinx.datetime.LocalDate

sealed class RecommendedItemInfo

data class RecommendedSubjectInfo(
    val bangumiId: Int,
    val nameCn: String,

    val name: String,
    val imageLarge: String,
    val nsfw: Boolean = false,
    val score: Double = 0.0,
    val scoreCount: Int = 0,
    val airDate: LocalDate? = null,
) : RecommendedItemInfo()

fun RecommendedSubjectInfo.preferredDisplayName(useOriginalTitle: Boolean): String =
    if (useOriginalTitle) name.ifBlank { nameCn } else nameCn.ifBlank { name }

val RecommendedItemInfo.id: Any
    get() = when (this) {
        is RecommendedSubjectInfo -> bangumiId
    }

val RecommendedItemInfo.type: Int
    get() = when (this) {
        is RecommendedSubjectInfo -> 0
    }

@TestOnly
val TestRecommendedItemInfos: List<RecommendedItemInfo>
    get() = TestFollowedSubjectInfos.map {
        RecommendedSubjectInfo(
            bangumiId = it.subjectInfo.subjectId,
            nameCn = it.subjectInfo.nameCn,
            name = it.subjectInfo.name,
            imageLarge = it.subjectInfo.imageLarge,
        )
    }
