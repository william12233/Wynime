package com.wynime.app.data.models.subject

import com.wynime.app.domain.mediasource.MediaListFilters

data class SubjectSeriesInfo(

    val seasonSort: Int,

    val sequelSubjectNames: Set<String>,

    val seriesSubjectNamesWithoutSelf: Set<String>,
) {
    companion object {
        fun compute(
            requestingSubject: SubjectCollectionInfo,
        ): SubjectSeriesInfo {
            val sequelSubjectNames = requestingSubject.relations.sequelSubjectNames.toMutableSet().apply {
                removeAll { sequelName ->

                    requestingSubject.subjectInfo.allNames.any {
                        MediaListFilters.specialContains(it, sequelName)
                    }
                }
            }
            val seriesSubjectNamesWithoutSelf: Set<String> =
                requestingSubject.relations.seriesMainSubjectNames.toMutableSet().apply {
                    removeAll { seriesName ->
                        requestingSubject.subjectInfo.allNames.any { subjectName ->
                            MediaListFilters.specialEquals(subjectName, seriesName)
                        }
                    }
                }

            val seasonSort = requestingSubject.relations.seriesMainSubjectIds
                .indexOfFirst { it == requestingSubject.subjectId }
                .let { if (it == -1) 1 else it + 1 }
            return SubjectSeriesInfo(

                seasonSort = seasonSort,
                sequelSubjectNames,
                seriesSubjectNamesWithoutSelf,
            )
        }

        val Fallback = SubjectSeriesInfo(

            seasonSort = 1,
            sequelSubjectNames = emptySet(),
            seriesSubjectNamesWithoutSelf = emptySet(),
        )
    }
}

fun SubjectSeriesInfo.toBuilder() = SubjectSeriesInfoBuilder(seasonSort).apply {
    sequel(*sequelSubjectNames.toTypedArray())
    series(*seriesSubjectNamesWithoutSelf.toTypedArray())
}

class SubjectSeriesInfoBuilder(

    var seasonSort: Int,
) {
    private val sequelNames = mutableListOf<String>()
    private val seriesNames = mutableListOf<String>()

    fun sequel(vararg subjectName: String) {
        sequelNames.addAll(subjectName)
        seriesNames.addAll(subjectName)
    }

    fun series(vararg subjectName: String) {
        seriesNames.addAll(subjectName)
    }

    fun build() = SubjectSeriesInfo(
        seasonSort, sequelSubjectNames = sequelNames.toSet(), seriesSubjectNamesWithoutSelf = seriesNames.toSet(),
    )
}

inline fun buildSubjectSeriesInfo(
    seasonSort: Int,
    block: SubjectSeriesInfoBuilder.() -> Unit
) =
    SubjectSeriesInfoBuilder(seasonSort).apply(block).build()
