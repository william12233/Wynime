@file:Suppress("unused", "UNUSED_VARIABLE")

package com.wynime.app.domain.mediasource

import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.Media
import com.wynime.test.Sample

@Sample
fun applyingFilters(mediaList: List<Media>) {

    val enableFilterBySubjectName = true
    val filters = buildList {

        if (enableFilterBySubjectName) {
            add(MediaListFilters.ContainsSubjectName)
        }
        add(MediaListFilters.ContainsAnyEpisodeInfo)
    }

    val context = MediaListFilterContext(
        setOf("条目名称"), EpisodeSort(1), EpisodeSort(1),
        "第1集",
    )
    val newList: List<Media> = with(context) {
        mediaList.filter { media ->
            filters.applyOn(media.asCandidate())
        }
    }
}
