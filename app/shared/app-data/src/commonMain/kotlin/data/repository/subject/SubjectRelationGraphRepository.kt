package com.wynime.app.data.repository.subject

import kotlinx.coroutines.CancellationException
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
                loadSubjectRelationGraph(subjectId)
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

    private suspend fun loadSubjectRelationGraph(subjectId: Int): OfficialRelationGraph {
        val subjects = linkedMapOf<Int, BangumiSubject>()
        val relations = linkedMapOf<Int, List<BangumiV0SubjectRelation>>()

        suspend fun loadSubject(id: Int): BangumiSubject {
            return subjects[id] ?: bangumiApi.request { getSubjectById(id) }.also { subject ->
                subjects[id] = subject
            }
        }

        suspend fun loadRelations(id: Int): List<BangumiV0SubjectRelation> {
            return relations[id] ?: bangumiApi.request { getRelatedSubjectsBySubjectId(id) }.also { related ->
                relations[id] = related
            }
        }

        val requestedSubject = loadSubject(subjectId)
        val requestedRelations = loadRelations(requestedSubject.id)
        val mainlineRootId = requestedRelations
            .firstOrNull { it.relation.toSubjectRelation() == SubjectRelation.MAIN_STORY }
            ?.id
            ?.takeUnless { it == requestedSubject.id }
            ?.let { parentId ->
                try {
                    loadSubject(parentId).id
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            ?: requestedSubject.id

        val relationMap = linkedMapOf<Int, List<RelationGraphLink>>()
        val pendingRelationSubjects = mutableListOf(mainlineRootId)
        val scheduledRelationSubjects = mutableSetOf(mainlineRootId)
        var relationTraversalTruncated = false
        var pendingIndex = 0
        while (pendingIndex < pendingRelationSubjects.size) {
            val currentId = pendingRelationSubjects[pendingIndex++]
            val currentRelations = loadRelations(currentId).map { relation ->
                RelationGraphLink(relation.id, relation.relation.toSubjectRelation())
            }
            relationMap[currentId] = currentRelations
            for (relation in currentRelations) {
                if (relation.relation != SubjectRelation.PREQUEL &&
                    relation.relation != SubjectRelation.SEQUEL
                ) continue
                if (scheduledRelationSubjects.add(relation.subjectId)) {
                    if (scheduledRelationSubjects.size > MAX_RELATION_GRAPH_SUBJECTS) {
                        scheduledRelationSubjects.remove(relation.subjectId)
                        relationTraversalTruncated = true
                    } else {
                        pendingRelationSubjects += relation.subjectId
                    }
                }
            }
        }

        val mainlineTraversal = buildRelationGraphMainline(mainlineRootId, relationMap)
        val mainlineIds = mainlineTraversal.subjectIds
        val mainlineIdSet = mainlineIds.toSet()
        val branchCandidatesByMainline = linkedMapOf<Int, MutableList<BranchCandidate>>()
        val seenBranches = mutableSetOf<Int>()
        var truncated = relationTraversalTruncated || mainlineTraversal.truncated

        for (mainlineId in mainlineIds) {
            val candidates = branchCandidatesByMainline.getOrPut(mainlineId) { mutableListOf() }
            for (relation in relationMap[mainlineId].orEmpty()) {
                val subjectRelation = relation.relation
                if (subjectRelation == SubjectRelation.PREQUEL ||
                    subjectRelation == SubjectRelation.SEQUEL ||
                    relation.subjectId in mainlineIdSet ||
                    !seenBranches.add(relation.subjectId)
                ) continue
                candidates += BranchCandidate(relation.subjectId, subjectRelation)
            }
        }

        if (requestedSubject.id !in mainlineIdSet && seenBranches.add(requestedSubject.id)) {
            branchCandidatesByMainline.getOrPut(mainlineRootId) { mutableListOf() }
                .add(BranchCandidate(requestedSubject.id, SubjectRelation.MAIN_STORY))
        }

        val branchCandidates = branchCandidatesByMainline.values.flatten()
        val branchLimit = (MAX_RELATION_GRAPH_SUBJECTS - mainlineIds.size).coerceAtLeast(0)
        if (branchCandidates.size > branchLimit) truncated = true
        val selectedBranchIds = branchCandidates.take(branchLimit).map { it.subjectId }.toSet()

        // Mainline cards render year, platform, and episode metadata. Branch rows only need
        // the compact relation payload, so avoid one full subject request per branch.
        for (id in mainlineIds) {
            loadSubject(id)
        }

        val relationSubjects = relations.values.flatten().associateBy { it.id }
        fun graphSubject(id: Int): SubjectRelationGraphSubject? = subjects[id]?.toGraphSubject()
            ?: relationSubjects[id]?.toGraphSubject()

        val mainline = mainlineIds.mapNotNull { id ->
            graphSubject(id)?.let { subject ->
                SubjectRelationGraphMainNode(
                    subject = subject,
                    isMinor = subject.isMinorMainlineNode(),
                    branches = branchCandidatesByMainline[id].orEmpty()
                        .filter { it.subjectId in selectedBranchIds }
                        .mapNotNull { branch ->
                            graphSubject(branch.subjectId)?.let { branchSubject ->
                                SubjectRelationGraphBranch(
                                    subject = branchSubject,
                                    relation = branch.relation,
                                )
                            }
                        },
                )
            }
        }

        return OfficialRelationGraph(
            graph = SubjectRelationGraph(
                subjectId = requestedSubject.id,
                mainline = mainline,
                truncated = truncated,
            ),
            subjectIds = mainline.flatMap { main ->
                listOf(main.subject.subjectId) + main.branches.map { it.subject.subjectId }
            }.distinct(),
        )
    }
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

private data class BranchCandidate(
    val subjectId: Int,
    val relation: SubjectRelation?,
)

internal data class RelationGraphLink(
    val subjectId: Int,
    val relation: SubjectRelation?,
)

internal data class RelationGraphMainline(
    val subjectIds: List<Int>,
    val truncated: Boolean,
)

internal fun buildRelationGraphMainline(
    rootSubjectId: Int,
    relations: Map<Int, List<RelationGraphLink>>,
    maxSubjects: Int = MAX_RELATION_GRAPH_SUBJECTS,
): RelationGraphMainline {
    val included = linkedSetOf(rootSubjectId)
    val prequelOrder = mutableListOf<Int>()
    val sequelOrder = mutableListOf<Int>()
    var truncated = false

    fun collectPrequels(subjectId: Int, visiting: MutableSet<Int>) {
        if (!visiting.add(subjectId)) return
        for (relation in relations[subjectId].orEmpty()) {
            if (relation.relation != SubjectRelation.PREQUEL || relation.subjectId in included) continue
            if (included.size >= maxSubjects) {
                truncated = true
                continue
            }
            included += relation.subjectId
            collectPrequels(relation.subjectId, visiting)
            prequelOrder += relation.subjectId
        }
        visiting.remove(subjectId)
    }

    fun collectSequels(subjectId: Int, visiting: MutableSet<Int>) {
        if (!visiting.add(subjectId)) return
        for (relation in relations[subjectId].orEmpty()) {
            if (relation.relation != SubjectRelation.SEQUEL || relation.subjectId in included) continue
            if (included.size >= maxSubjects) {
                truncated = true
                continue
            }
            included += relation.subjectId
            sequelOrder += relation.subjectId
            collectSequels(relation.subjectId, visiting)
        }
        visiting.remove(subjectId)
    }

    collectPrequels(rootSubjectId, mutableSetOf())
    collectSequels(rootSubjectId, mutableSetOf())
    return RelationGraphMainline(
        subjectIds = (prequelOrder + rootSubjectId + sequelOrder).distinct(),
        truncated = truncated,
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
    platform = null,
    episodeCount = 0,
    collectionType = UnifiedCollectionType.NOT_COLLECTED,
)

internal fun SubjectRelationGraphSubject.isMinorMainlineNode(): Boolean = when {
    episodeCount >= MAINLINE_EPISODE_THRESHOLD -> false
    episodeCount in 1 until MAINLINE_EPISODE_THRESHOLD -> true
    platform == SubjectRelationGraphPlatform.OVA || platform == SubjectRelationGraphPlatform.MOVIE -> true
    else -> false
}

private fun String?.toPackedDate(): PackedDate =
    if (isNullOrBlank()) PackedDate.Invalid else PackedDate.parseFromDate(requireNotNull(this))

private fun String.toGraphPlatform(): SubjectRelationGraphPlatform? = when {
    contains("tv", ignoreCase = true) -> SubjectRelationGraphPlatform.TV
    contains("ova", ignoreCase = true) -> SubjectRelationGraphPlatform.OVA
    contains("movie", ignoreCase = true) || contains("剧场") || contains("劇場") ||
        contains("電影") || contains("电影") ->
        SubjectRelationGraphPlatform.MOVIE
    contains("web", ignoreCase = true) -> SubjectRelationGraphPlatform.WEB
    else -> null
}

private const val MAINLINE_EPISODE_THRESHOLD = 8
internal const val MAX_RELATION_GRAPH_SUBJECTS = 100

private fun String.toSubjectRelation(): SubjectRelation? = when (trim().lowercase()) {
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
