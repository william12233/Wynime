package com.wynime.app.ui.subject.episode.details

import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.domain.episode.SubjectRecommendation
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.utils.platform.annotations.TestOnly

@Stable
@TestOnly
internal val PreviewTestEpisodes = buildList {
    repeat(12) { id ->
        add(
            EpisodeInfo(
                id,
                EpisodeType.MainStory,
                nameCn = if (id.rem(2) == 0) {
                    "中文剧集名称中文剧集名称中文剧集名称中文剧集名称"
                } else {
                    "中文剧集名称"
                },
                name = "Episode Name $id",
                sort = EpisodeSort((24 + id).toString()),
                ep = EpisodeSort(id.toString()),
            ),
        )
    }
}

@Stable
@TestOnly
internal val PreviewEpisodeCollections = PreviewTestEpisodes.map {
    EpisodeCollectionInfo(
        it,
        when ((it.ep?.number ?: 0).toInt().rem(3)) {
            0 -> UnifiedCollectionType.DONE
            1 -> UnifiedCollectionType.WISH
            else -> UnifiedCollectionType.DOING
        },
    )
}

@Stable
@TestOnly
internal val PreviewScope = CoroutineScope(
    CoroutineExceptionHandler { _, _ -> },
)

@Stable
@TestOnly
internal val PreviewSubjectRecommendations = buildList {
    repeat(10) {
        add(
            SubjectRecommendation(
                subjectId = it.toLong(),
                name = "Subject Recommendation $it",
                nameCn = "推荐条目中文名称 $it",
                imageUrl = "",
                uri = null,
                desc1 = "2021 年 10 月",
                desc2 = "2 万收藏 · 8.0 分",
            ),
        )
    }
}
