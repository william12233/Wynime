package com.wynime.app.data.repository.subject

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.subject.SubjectRelation
import com.wynime.app.data.models.subject.SubjectRelationGraph
import com.wynime.app.data.models.subject.SubjectRelationGraphBranch
import com.wynime.app.data.models.subject.SubjectRelationGraphMainNode
import com.wynime.app.data.models.subject.SubjectRelationGraphPlatform
import com.wynime.app.data.models.subject.SubjectRelationGraphSubject
import com.wynime.app.data.persistent.database.dao.SubjectCollectionDao
import com.wynime.app.data.network.BangumiApiProvider
import com.wynime.app.data.repository.Repository
import com.wynime.app.data.repository.RepositoryException
import com.wynime.models.SubjectRelationGraphDto
import com.wynime.models.SubjectRelationGraphNodeDto
import com.wynime.models.SubjectRelationGraphNodeRoleDto
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.datasources.bangumi.models.BangumiSubject
import com.wynime.datasources.bangumi.models.BangumiV0SubjectRelation
import kotlin.coroutines.CoroutineContext

class SubjectRelationGraphRepository(
    private val bangumiApi: BangumiApiProvider,
    private val subjectCollectionDao: SubjectCollectionDao,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {

    fun subjectRelationGraphFlow(subjectId: Int): Flow<SubjectRelationGraph> = flow {
        val graph = try {
            withContext(defaultDispatcher) {
                val subject = bangumiApi.request { getSubjectById(subjectId) }
                val relations = bangumiApi.request { getRelatedSubjectsBySubjectId(subjectId) }
                subject.toSubjectRelationGraph(relations)
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }

        val seenLocally = mutableSetOf<Int>()
        emitAll(
            subjectCollectionDao.filterByIds(graph.subjectIds.toIntArray()).map { collections ->
                val local = collections.associate { it.subjectId to it.collectionType }
                val removed = (seenLocally - local.keys).associateWith { UnifiedCollectionType.NOT_COLLECTED }
                seenLocally += local.keys
                graph.withCollections(local + removed)
            },
        )
    }.flowOn(defaultDispatcher)
}

private data class OfficialRelationGraph(
    val graph: SubjectRelationGraph,
    val subjectIds: List<Int>,
) {
    fun withCollections(collectionTypes: Map<Int, UnifiedCollectionType>): SubjectRelationGraph {
        fun SubjectRelationGraphSubject.withCollection() = copy(
            collectionType = collectionTypes[subjectId] ?: UnifiedCollectionType.NOT_COLLECTED,
        )

        return graph.copy(
            mainline = graph.mainline.map { main ->
                main.copy(
                    subject = main.subject.withCollection(),
                    branches = main.branches.map { branch ->
                        branch.copy(subject = branch.subject.withCollection())
                    },
                )
            },
        )
    }
}

private fun BangumiSubject.toSubjectRelationGraph(
    relations: List<BangumiV0SubjectRelation>,
): OfficialRelationGraph {
    val prequels = relations.filter { it.relation.toSubjectRelation() == SubjectRelation.PREQUEL }
    val sequels = relations.filter { it.relation.toSubjectRelation() == SubjectRelation.SEQUEL }
    val mainline = prequels.map { it.toGraphSubject() } +
        listOf(toGraphSubject()) +
        sequels.map { it.toGraphSubject() }
    val branches = relations
        .filter { it.relation.toSubjectRelation() !in setOf(SubjectRelation.PREQUEL, SubjectRelation.SEQUEL) }
        .map { relation ->
            SubjectRelationGraphBranch(
                subject = relation.toGraphSubject(),
                relation = relation.relation.toSubjectRelation(),
            )
        }
    return OfficialRelationGraph(
        graph = SubjectRelationGraph(
            subjectId = id,
            mainline = mainline.mapIndexed { index, graphSubject ->
                SubjectRelationGraphMainNode(
                    subject = graphSubject,
                    isMinor = index != prequels.size,
                    branches = if (index == prequels.size) branches else emptyList(),
                )
            },
            truncated = false,
        ),
        subjectIds = (mainline.map { it.subjectId } + branches.map { it.subject.subjectId }).distinct(),
    )
}

private fun BangumiSubject.toGraphSubject() = SubjectRelationGraphSubject(
    subjectId = id,
    name = name,
    nameCn = nameCn,
    image = images.large,
    airDate = date.toPackedDate(),
    platform = platform.toGraphPlatform(),
    episodeCount = totalEpisodes,
    collectionType = UnifiedCollectionType.NOT_COLLECTED,
)

private fun BangumiV0SubjectRelation.toGraphSubject() = SubjectRelationGraphSubject(
    subjectId = id,
    name = name,
    nameCn = nameCn,
    image = images?.large.orEmpty(),
    airDate = PackedDate.Invalid,
    platform = when (type) {
        2 -> SubjectRelationGraphPlatform.TV
        else -> null
    },
    episodeCount = 0,
    collectionType = UnifiedCollectionType.NOT_COLLECTED,
)

private fun String?.toPackedDate(): PackedDate =
    if (isNullOrBlank()) PackedDate.Invalid else PackedDate.parseFromDate(requireNotNull(this))

private fun String.toGraphPlatform(): SubjectRelationGraphPlatform? = when {
    contains("tv", ignoreCase = true) -> SubjectRelationGraphPlatform.TV
    contains("ova", ignoreCase = true) -> SubjectRelationGraphPlatform.OVA
    contains("movie", ignoreCase = true) || contains("剧场") || contains("電影") ->
        SubjectRelationGraphPlatform.MOVIE
    contains("web", ignoreCase = true) -> SubjectRelationGraphPlatform.WEB
    else -> null
}

private fun String.toSubjectRelation(): SubjectRelation? = when (lowercase()) {
    "前传", "前傳", "prequel" -> SubjectRelation.PREQUEL
    "续集", "續集", "sequel" -> SubjectRelation.SEQUEL
    "衍生", "derived" -> SubjectRelation.DERIVED
    "番外篇", "番外", "special" -> SubjectRelation.SPECIAL
    "主线故事", "主線故事", "主线", "主線", "main story" -> SubjectRelation.MAIN_STORY
    "总集篇", "總集篇", "compilation" -> SubjectRelation.COMPILATION
    else -> null
}

internal fun SubjectRelationGraphDto.toSubjectRelationGraph(
    collectionTypes: Map<Int, UnifiedCollectionType>,
): SubjectRelationGraph {
    fun SubjectRelationGraphNodeDto.toSubject() = SubjectRelationGraphSubject(
        subjectId = id.toInt(),
        name = name,
        nameCn = nameCn,
        image = imageLarge,
        airDate = if (airDate.isEmpty()) PackedDate.Invalid else PackedDate.parseFromDate(airDate),
        platform = when (platform) {
            1 -> SubjectRelationGraphPlatform.TV
            2 -> SubjectRelationGraphPlatform.OVA
            3 -> SubjectRelationGraphPlatform.MOVIE
            5 -> SubjectRelationGraphPlatform.WEB
            else -> null
        },
        episodeCount = episodeCount,
        collectionType = collectionTypes[id.toInt()] ?: collectionType.toUnifiedCollectionType(),
    )

    val nodesById = nodes.associateBy { it.id }

    val sideNodes = nodes.filter { it.role == SubjectRelationGraphNodeRoleDto.SIDE }.groupBy { it.attachTo }

    return SubjectRelationGraph(
        subjectId = subjectId.toInt(),
        mainline = mainline.mapNotNull { nodesById[it] }.map { node ->
            SubjectRelationGraphMainNode(
                subject = node.toSubject(),
                isMinor = node.role != SubjectRelationGraphNodeRoleDto.MAIN,
                branches = sideNodes[node.id].orEmpty().map { branch ->
                    SubjectRelationGraphBranch(
                        subject = branch.toSubject(),
                        relation = when (branch.relation) {
                            4 -> SubjectRelation.COMPILATION
                            6 -> SubjectRelation.SPECIAL
                            11 -> SubjectRelation.DERIVED
                            12 -> SubjectRelation.MAIN_STORY
                            else -> null
                        },
                    )
                },
            )
        },
        truncated = truncated,
    )
}
