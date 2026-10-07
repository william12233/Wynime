package com.wynime.app.data.models.subject

import androidx.compose.runtime.Immutable
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.preference.NsfwMode
import com.wynime.app.data.persistent.database.dao.SubjectRelations
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Instant

@Immutable
data class SubjectCollectionInfo(
    val collectionType: UnifiedCollectionType,
    val subjectInfo: SubjectInfo,
    val selfRatingInfo: SelfRatingInfo,
    val episodes: List<EpisodeCollectionInfo>,
    val airingInfo: SubjectAiringInfo,
    val progressInfo: SubjectProgressInfo,

    val recurrence: SubjectRecurrence?,
    val cachedStaffUpdated: Long,
    val cachedCharactersUpdated: Long,

    val lastUpdated: Long,
    val nsfwMode: NsfwMode,

    val relations: SubjectRelations,
) {
    val subjectId: Int get() = subjectInfo.subjectId
}

data class SubjectRecurrence(

    val startTime: Instant,

    val interval: Duration,
)

@TestOnly
val TestSubjectCollections
    get() = buildList {
        var id = 0
        val eps = listOf(
            EpisodeCollectionInfo(
                episodeInfo = EpisodeInfo(
                    episodeId = 6385,
                    type = EpisodeType.MainStory,
                    name = "Diana Houston",
                    nameCn = "Nita O'Donnell",
                    comment = 5931,
                    desc = "gubergren",
                    sort = EpisodeSort(1),
                    ep = EpisodeSort(1),
                ),
                collectionType = UnifiedCollectionType.DONE,
            ),
            EpisodeCollectionInfo(
                episodeInfo = EpisodeInfo(
                    episodeId = 6386,
                    type = EpisodeType.MainStory,
                    name = "Diana Houston",
                    nameCn = "Nita O'Donnell",
                    sort = EpisodeSort(2),
                    comment = 5931,
                    desc = "gubergren",
                    ep = EpisodeSort(2),
                ),
                collectionType = UnifiedCollectionType.DONE,
            ),

            )
        add(
            createTestSubjectCollection(++id, eps, UnifiedCollectionType.DOING),
        )
        add(
            createTestSubjectCollection(++id, eps, UnifiedCollectionType.DOING),
        )
        add(
            createTestSubjectCollection(++id, eps, UnifiedCollectionType.DOING),
        )
        add(
            createTestSubjectCollection(++id, eps, collectionType = UnifiedCollectionType.WISH),
        )
        repeat(10) {
            add(
                createTestSubjectCollection(
                    ++id,
                    episodes = eps + EpisodeCollectionInfo(
                        episodeInfo = EpisodeInfo(
                            episodeId = 6386,
                            type = EpisodeType.MainStory,
                            name = "Diana Houston",
                            nameCn = "Nita O'Donnell",
                            sort = EpisodeSort(2),
                            comment = 5931,
                            desc = "gubergren",
                            ep = EpisodeSort(2),
                        ),
                        collectionType = UnifiedCollectionType.DONE,
                    ),
                    collectionType = UnifiedCollectionType.WISH,
                ),
            )
        }
    }

@TestOnly
fun createTestSubjectCollection(
    id: Int,
    episodes: List<EpisodeCollectionInfo>,
    collectionType: UnifiedCollectionType,
    nsfwMode: NsfwMode = NsfwMode.DISPLAY,
): SubjectCollectionInfo {
    val subjectInfo = SubjectInfo.Empty.copy(
        subjectId = id,
        nameCn = "中文条目名称",
        name = "Subject Name",
        nsfw = Random.nextBoolean(),
    )
    return SubjectCollectionInfo(
        collectionType = collectionType,
        subjectInfo = subjectInfo,
        selfRatingInfo = TestSelfRatingInfo,
        episodes = episodes,
        airingInfo = SubjectAiringInfo.computeFromEpisodeList(
            episodes.map { it.episodeInfo },
            airDate = subjectInfo.airDate,
            recurrence = null,
        ),
        progressInfo = SubjectProgressInfo.compute(
            subjectInfo,
            episodes,
            PackedDate.now(),
            recurrence = null,
        ),
        recurrence = null,
        cachedStaffUpdated = 0,
        cachedCharactersUpdated = 0,
        lastUpdated = 0,
        nsfwMode = nsfwMode,
        relations = SubjectRelations.Empty,
    )
}
