package com.wynime.app.data.models.subject

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.wynime.app.domain.search.SubjectType
import com.wynime.app.navigation.SubjectDetailPlaceholder
import com.wynime.datasources.api.PackedDate
import com.wynime.utils.platform.annotations.TestOnly

@Immutable
data class SubjectInfo(

    val subjectId: Int,
    val subjectType: SubjectType,

    val name: String,

    val nameCn: String,

    val summary: String,
    val nsfw: Boolean,
    val imageLarge: String,

    val imageThumb: String = "",

    @Deprecated("This includes all MainStory/OVA/SP while the app only supports MainStory")
    val totalEpisodes: Int,

    val airDate: PackedDate,

    val tags: List<Tag>,

    val aliases: List<String>,

    val ratingInfo: RatingInfo,

    val collectionStats: SubjectCollectionStats,

    @Deprecated("Removed, because we always have episodes now")
    val completeDate: PackedDate,

    val tmdbArt: SubjectTmdbArt? = null,
) {
    override fun toString(): String {
        return "SubjectInfo(subjectId=$subjectId, nameCn='$nameCn')"
    }

    val displayName: String get() = nameCn.takeIf { it.isNotBlank() } ?: name

    val allNames by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildList {

            fun addIfNotBlank(name2: String) {
                if (name2.isNotBlank()) add(name2)
            }
            addIfNotBlank(nameCn)
            addIfNotBlank(name)
            aliases.forEach { addIfNotBlank(it) }
        }
    }

    companion object {
        @Stable
        val Empty = SubjectInfo(
            subjectId = 0,
            subjectType = SubjectType.ANIME,
            name = "",
            nameCn = "",
            summary = "",
            nsfw = false,
            imageLarge = "",
            totalEpisodes = 0,
            airDate = PackedDate.Invalid,
            tags = emptyList(),
            aliases = emptyList(),
            ratingInfo = RatingInfo.Empty,
            collectionStats = SubjectCollectionStats.Zero,
            completeDate = PackedDate.Invalid,
        )

        fun createPlaceholder(subjectId: Int, name: String, image: String, nameCn: String = ""): SubjectInfo {
            return SubjectInfo(
                subjectId = subjectId,
                subjectType = SubjectType.ANIME,
                name = name,
                nameCn = nameCn,
                summary = "",
                nsfw = false,
                imageLarge = image,
                totalEpisodes = 0,
                airDate = PackedDate.Invalid,
                tags = emptyList(),
                aliases = emptyList(),
                ratingInfo = RatingInfo.Empty,
                collectionStats = SubjectCollectionStats.Zero,
                completeDate = PackedDate.Invalid,
            )
        }
    }
}

@Stable
val SubjectInfo.nameCnOrName get() = nameCn.takeIf { it.isNotBlank() } ?: name

@Stable
val SubjectInfo.listCoverUrl: String get() = imageLarge.ifEmpty { imageThumb }

@Stable
val SubjectInfo.nameOrNameCn get() = name.ifBlank { nameCn }

fun SubjectInfo.preferredDisplayName(useOriginalTitle: Boolean): String =
    if (useOriginalTitle) nameOrNameCn else displayName

fun SubjectInfo.toNavPlaceholder(): SubjectDetailPlaceholder {
    return SubjectDetailPlaceholder(subjectId, name, nameCn, imageLarge)
}

@TestOnly
val TestSubjectInfo
    get() = SubjectInfo.Empty.copy(
        nameCn = "孤独摇滚！",
        name = "ぼっち・ざ・ろっく！",
        airDate = PackedDate(2023, 10, 1),
        summary = """
        作为网络吉他手“吉他英雄”而广受好评的后藤一里，在现实中却是个什么都不会的沟通障碍者。一里有着组建乐队的梦想，但因为不敢向人主动搭话而一直没有成功，直到一天在公园中被伊地知虹夏发现并邀请进入缺少吉他手的“结束乐队”。可是，完全没有和他人合作经历的一里，在人前完全发挥不出原本的实力。为了努力克服沟通障碍，一里与“结束乐队”的成员们一同开始努力……
    """.trimIndent(),
        tags = listOf(
            Tag("芳文社", 7098),
            Tag("音乐", 5000),
            Tag("CloverWorks", 5000),
            Tag("轻百合", 4000),
            Tag("日常", 3758),
        ),
        ratingInfo = TestRatingInfo,
        collectionStats = TestCollectionStats,
    )

@TestOnly
val TestRatingInfo
    get() = RatingInfo(
        rank = 123,
        total = 100,
        count = RatingCounts(IntArray(10) { it * 10 }),
        score = "6.7",
    )

@TestOnly
val TestCollectionStats
    get() = SubjectCollectionStats(
        wish = 100,
        doing = 200,
        done = 300,
        onHold = 400,
        dropped = 500,
    )

@TestOnly
const val TestCoverImage = "https://ui-avatars.com/api/?name=John+Doe"
