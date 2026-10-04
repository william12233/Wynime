/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectRelation

class BangumiRelatedPeopleService(
    private val bangumiApi: BangumiApiProvider,
) {
    fun relatedSubjectsFlow(subjectId: Int): Flow<List<RelatedSubjectInfo>> = flow {
        val list = bangumiApi.request { getRelatedSubjectsBySubjectId(subjectId) }
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

private fun String.toSubjectRelation() = when (trim().lowercase()) {
    "前传", "prequel" -> SubjectRelation.PREQUEL
    "续集", "sequel" -> SubjectRelation.SEQUEL
    "衍生", "derived" -> SubjectRelation.DERIVED
    "番外篇", "番外", "special" -> SubjectRelation.SPECIAL
    "主线故事", "主线", "main story" -> SubjectRelation.MAIN_STORY
    "总集篇", "compilation" -> SubjectRelation.COMPILATION
    else -> null
}
