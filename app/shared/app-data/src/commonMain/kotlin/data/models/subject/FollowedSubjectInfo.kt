package com.wynime.app.data.models.subject

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.wynime.app.data.models.preference.NsfwMode
import com.wynime.utils.platform.annotations.TestOnly

@Immutable
data class FollowedSubjectInfo(
    val subjectCollectionInfo: SubjectCollectionInfo,
    val subjectAiringInfo: SubjectAiringInfo,
    val subjectProgressInfo: SubjectProgressInfo,
    val nsfwMode: NsfwMode,
)

@Stable
val FollowedSubjectInfo.subjectInfo get() = subjectCollectionInfo.subjectInfo

@TestOnly
fun createTestFollowedSubjectInfo(
    subjectCollectionInfo: SubjectCollectionInfo,
    subjectAiringInfo: SubjectAiringInfo,
    subjectProgressInfo: SubjectProgressInfo,
    nsfwMode: NsfwMode = if (subjectCollectionInfo.subjectInfo.nsfw) NsfwMode.BLUR else NsfwMode.DISPLAY,
) = FollowedSubjectInfo(
    subjectCollectionInfo,
    subjectAiringInfo,
    subjectProgressInfo,
    nsfwMode,
)

@TestOnly
val TestFollowedSubjectInfos
    get() = listOf(
        createTestFollowedSubjectInfo(
            TestSubjectCollections[0],
            TestSubjectAiringInfos.OnAir12Eps,
            TestSubjectProgressInfos.ContinueWatching2,
        ),
        createTestFollowedSubjectInfo(
            TestSubjectCollections[1],
            TestSubjectAiringInfos.Upcoming24Eps,
            TestSubjectProgressInfos.NotOnAir,
        ),
        createTestFollowedSubjectInfo(
            TestSubjectCollections[2],
            TestSubjectAiringInfos.OnAir12Eps,
            TestSubjectProgressInfos.Watched2,
        ),
        createTestFollowedSubjectInfo(
            TestSubjectCollections[3],
            TestSubjectAiringInfos.Completed12Eps,
            TestSubjectProgressInfos.Done,
        ),
    )