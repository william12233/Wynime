/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext


// For 查询第一季时自动排除第二季的资源 #1324
class AniSubjectRelationIndexService(
    private val bangumiApi: BangumiApiProvider,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    suspend fun getSubjectRelationIndex(subjectId: Int) = withContext(ioDispatcher) {
        try {
            val relations = bangumiApi.request { getRelatedSubjectsBySubjectId(subjectId) }
            val prequels = relations.filter { it.relation.isPrequel() }.map { it.id }.asReversed()
            BangumiSubjectRelationIndex(
                sequelSubjects = relations.filter { it.relation.isSequel() }.map { it.id },
                seriesMainSubjectIds = (prequels + subjectId + relations.filter { it.relation.isSequel() }.map { it.id })
                    .distinct(),
            )
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}

data class BangumiSubjectRelationIndex(
    val sequelSubjects: List<Int>,
    val seriesMainSubjectIds: List<Int>,
)

private fun String.isPrequel() = trim().lowercase() in setOf("前传", "prequel")

private fun String.isSequel() = trim().lowercase() in setOf("续集", "sequel")
