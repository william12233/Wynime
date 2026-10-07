package com.wynime.app.ui.exploration.recommend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import com.wynime.app.data.models.recommend.RecommendedItemInfo
import com.wynime.app.data.models.recommend.RecommendedSubjectInfo
import com.wynime.app.data.models.recommend.TestRecommendedItemInfos
import com.wynime.app.data.models.recommend.id
import com.wynime.app.data.models.recommend.preferredDisplayName
import com.wynime.app.data.models.recommend.type
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme
import com.wynime.app.ui.foundation.layout.CarouselItemDefaults
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.minimumHairlineSize
import com.wynime.app.ui.search.LoadErrorCard
import com.wynime.app.ui.search.createTestPager
import com.wynime.app.ui.search.rememberLoadErrorState
import com.wynime.app.ui.subject.SubjectCoverCard
import com.wynime.app.ui.subject.SubjectGridDefaults
import com.wynime.app.ui.subject.SubjectGridLayoutParams
import com.wynime.utils.platform.annotations.TestOnly

fun LazyGridScope.recommendationItems(
    data: LazyPagingItems<RecommendedItemInfo>,
    loadError: LoadError?,
    onClick: (RecommendedItemInfo) -> Unit,
    layoutParams: RecommendationLayoutParams,
) {
    if (loadError != null) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.minimumHairlineSize()) {
                LoadErrorCard(loadError, { data.refresh() })
            }
        }
    }
    items(
        data.itemCount,
        key = { index ->
            val item = data.peek(index)
            if (item == null) {
                "recommendation-placeholder-$index"
            } else {
                "recommendation-$index-${item.id}"
            }
        },
        contentType = data.itemContentType { it.type },
    ) { index ->
        val wynimeMotionScheme = LocalWynimeMotionScheme.current
        when (val item = data[index]) {
            null,
            is RecommendedSubjectInfo -> {
                val animateItem = Modifier
                    .animateItem(
                        fadeInSpec = wynimeMotionScheme.feedItemFadeInSpec,
                        fadeOutSpec = wynimeMotionScheme.feedItemFadeOutSpec,
                        placementSpec = wynimeMotionScheme.feedItemPlacementSpec,
                    )
                RecommendedSubjectCard(
                    item = item,
                    onClick = { item?.let { onClick(it) } },
                    modifier = animateItem,
                    shape = layoutParams.cardShape,
                )
            }
        }
    }
}

@Composable
private fun RecommendedSubjectCard(
    item: RecommendedSubjectInfo?,
    onClick: () -> Unit,
    shape: Shape = CarouselItemDefaults.shape,
    modifier: Modifier = Modifier,
) {
    val useOriginalTitle = LocalSubjectAppearanceSettings.current.useOriginalTitle
    SubjectCoverCard(
        name = item?.preferredDisplayName(useOriginalTitle),
        image = item?.imageLarge,
        isPlaceholder = item == null,
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        imageModifier = Modifier.sizeIn(maxWidth = 300.dp, maxHeight = (300f / 9 * 16).dp),
    )
}

typealias RecommendationLayoutParams = SubjectGridLayoutParams

@Stable
object RecommendationDefaults {
    @Composable
    fun layoutParameters(windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo1()): RecommendationLayoutParams {
        return SubjectGridDefaults.coverLayoutParameters(windowAdaptiveInfo)
    }
}

@OptIn(TestOnly::class)
@Composable
@PreviewScreenSizes
private fun PreviewRecommendationVerticalGrid() {
    ProvideCompositionLocalsForPreview {
        val layoutParams = RecommendationDefaults.layoutParameters()
        val data = createTestPager(TestRecommendedItemInfos).collectAsLazyPagingItems()
        val loadError by data.rememberLoadErrorState()
        LazyVerticalGrid(
            layoutParams.gridCells,
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = layoutParams.horizontalArrangement,
            verticalArrangement = layoutParams.verticalArrangement,
        ) {
            recommendationItems(
                data = data,
                loadError = loadError,
                onClick = {},
                layoutParams = layoutParams,
            )
        }
    }
}
