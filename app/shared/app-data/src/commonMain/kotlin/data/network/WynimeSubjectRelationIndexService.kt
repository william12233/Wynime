package com.wynime.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wynime.app.data.repository.RepositoryException
import com.wynime.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext

class WynimeSubjectRelationIndexService(
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
