package com.wynime.app.data.models.subject

import androidx.compose.runtime.Immutable
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.topic.UnifiedCollectionType

@Immutable
data class SubjectRelationGraph(

    val subjectId: Int,

    val mainline: List<SubjectRelationGraphMainNode>,

    val truncated: Boolean,
) {
    val mainCount: Int get() = mainline.size
    val branchCount: Int get() = mainline.sumOf { it.branches.size }
}

@Immutable
data class SubjectRelationGraphMainNode(
    val subject: SubjectRelationGraphSubject,

    val isMinor: Boolean,

    val branches: List<SubjectRelationGraphBranch>,
)

@Immutable
data class SubjectRelationGraphBranch(
    val subject: SubjectRelationGraphSubject,

    val relation: SubjectRelation?,
)

@Immutable
data class SubjectRelationGraphSubject(
    val subjectId: Int,
    val name: String,
    val nameCn: String,
    val image: String,
    val airDate: PackedDate,
    val platform: SubjectRelationGraphPlatform?,

    val episodeCount: Int,

    val collectionType: UnifiedCollectionType,
) {
    val displayName: String get() = nameCn.ifBlank { name }
}

enum class SubjectRelationGraphPlatform {
    TV, OVA, MOVIE, WEB,
}
