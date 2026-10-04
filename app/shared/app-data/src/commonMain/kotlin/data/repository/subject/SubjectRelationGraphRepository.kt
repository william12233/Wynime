/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.subject.SubjectRelation
import me.him188.ani.app.data.models.subject.SubjectRelationGraph
import me.him188.ani.app.data.models.subject.SubjectRelationGraphBranch
import me.him188.ani.app.data.models.subject.SubjectRelationGraphMainNode
import me.him188.ani.app.data.models.subject.SubjectRelationGraphPlatform
import me.him188.ani.app.data.models.subject.SubjectRelationGraphSubject
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionDao
import me.him188.ani.app.data.network.BangumiApiProvider
import me.him188.ani.app.data.repository.Repository
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.models.AniSubjectRelationGraph
import me.him188.ani.client.models.AniSubjectRelationGraphNode
import me.him188.ani.client.models.AniSubjectRelationGraphNodeRole
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.bangumi.models.BangumiSubject
import me.him188.ani.datasources.bangumi.models.BangumiV0SubjectRelation
import kotlin.coroutines.CoroutineContext

class SubjectRelationGraphRepository(
    private val bangumiApi: BangumiApiProvider,
    private val subjectCollectionDao: SubjectCollectionDao,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {
    /**
     * 获取 [subjectId] 所在系列的关系图. 图的结构由服务器计算, 只请求一次.
     *
     * 本地缓存只包含用户打开过或在收藏列表中加载过的条目, 无法区分 "未收藏" 和 "未缓存", 因此收藏状态以服务器随图返回的为基础,
     * 本地缓存中有记录的条目以本地为准, 并随收藏变化更新.
     */
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
        // 本地记录消失说明用户在此期间取消了收藏, 此时不能回退到服务器在请求时返回的状态
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

/**
 * @param collectionTypes 覆盖服务器返回的收藏状态. 不在其中的条目使用服务器返回的.
 */
internal fun AniSubjectRelationGraph.toSubjectRelationGraph(
    collectionTypes: Map<Int, UnifiedCollectionType>,
): SubjectRelationGraph {
    fun AniSubjectRelationGraphNode.toSubject() = SubjectRelationGraphSubject(
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
    // 服务器保证 nodes 中的分支已按挂载点和放送日期排序
    val sideNodes = nodes.filter { it.role == AniSubjectRelationGraphNodeRole.SIDE }.groupBy { it.attachTo }

    return SubjectRelationGraph(
        subjectId = subjectId.toInt(),
        mainline = mainline.mapNotNull { nodesById[it] }.map { node ->
            SubjectRelationGraphMainNode(
                subject = node.toSubject(),
                isMinor = node.role != AniSubjectRelationGraphNodeRole.MAIN,
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
