package com.wynime.app.data.models.subject

import androidx.compose.runtime.Immutable

@Immutable
class RelatedSubjectInfo(
    val subjectId: Int,

    val relation: SubjectRelation?,
    val name: String?,
    val nameCn: String,
    val image: String?,
) {
    val displayName get() = nameCn.ifBlank { name } ?: nameCn

    fun preferredDisplayName(useOriginalTitle: Boolean): String =
        if (useOriginalTitle) name?.ifBlank { nameCn } ?: nameCn else displayName

    companion object {
        fun sortList(subjectList: List<RelatedSubjectInfo>): List<RelatedSubjectInfo> {
            return subjectList.sortedByDescending {
                when (it.relation) {
                    SubjectRelation.PREQUEL -> 10
                    SubjectRelation.SEQUEL -> 9
                    SubjectRelation.DERIVED -> 8
                    SubjectRelation.SPECIAL -> 7
                    else -> 0
                }
            }
        }
    }
}

enum class SubjectRelation {

    SEQUEL,

    PREQUEL,

    DERIVED,

    SPECIAL,

    MAIN_STORY,

    COMPILATION,
}
