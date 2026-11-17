package com.wynime.app.data.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.wynime.app.data.models.subject.RelatedSubjectInfo
import com.wynime.app.data.models.subject.SubjectRelation
import com.wynime.datasources.bangumi.models.BangumiSubjectType
import com.wynime.datasources.bangumi.models.BangumiV0SubjectRelation

class BangumiRelatedPeopleService(
    private val bangumiApi: BangumiApiProvider,
) {
    fun relatedSubjectsFlow(subjectId: Int): Flow<List<RelatedSubjectInfo>> = flow {
        val list = bangumiApi.getRelatedAnimeSubjects(subjectId)
        emit(
            list.map { subject ->
                RelatedSubjectInfo(
                    subjectId = subject.id,
                    relation = subject.relation.toSubjectRelation(),
                    name = subject.name,
                    nameCn = subject.nameCn,
                    image = subject.images?.large,
                )
            }.let(RelatedSubjectInfo::sortList),
        )
    }
}

internal suspend fun BangumiApiProvider.getRelatedAnimeSubjects(subjectId: Int): List<BangumiV0SubjectRelation> =
    request { getRelatedSubjectsBySubjectId(subjectId) }.filter { it.type == BangumiSubjectType.Anime.value }

private fun String.toSubjectRelation() = when (trim().lowercase()) {
    "前传", "prequel" -> SubjectRelation.PREQUEL
    "续集", "sequel" -> SubjectRelation.SEQUEL
    "衍生", "derived" -> SubjectRelation.DERIVED
    "番外篇", "番外", "special" -> SubjectRelation.SPECIAL
    "主线故事", "主线", "main story" -> SubjectRelation.MAIN_STORY
    "总集篇", "compilation" -> SubjectRelation.COMPILATION
    else -> null
}
