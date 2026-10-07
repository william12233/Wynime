@file:OptIn(TestOnly::class)

package com.wynime.app.ui.exploration.today

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.exploration.TodayUpdateSubjectInfo
import com.wynime.app.ui.external.placeholder.placeholder
import com.wynime.app.ui.foundation.AsyncImage
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.layout.CarouselAutoAdvanceEffect
import com.wynime.app.ui.foundation.layout.CarouselItem
import com.wynime.app.ui.foundation.layout.CarouselItemDefaults
import com.wynime.app.ui.foundation.layout.minimumHairlineSize
import com.wynime.app.ui.foundation.preview.PreviewSizeClasses
import com.wynime.app.ui.search.LoadErrorCard
import com.wynime.app.domain.foundation.LoadError
import com.wynime.utils.platform.annotations.TestOnly

@Composable
fun TodayUpdatesCarousel(
    items: List<TodayUpdateSubjectInfo>,
    isInitialLoading: Boolean,
    error: LoadError?,
    onRetry: () -> Unit,
    onClick: (TodayUpdateSubjectInfo) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 8.dp,
    modifier: Modifier = Modifier,
    carouselState: CarouselState = rememberCarouselState(initialItem = 0) {
        if (isInitialLoading) 8 else items.size
    },
) {
    val size = CarouselItemDefaults.itemSize()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Box(modifier.padding(contentPadding).hoverable(interactionSource)) {
        if (isInitialLoading || items.isNotEmpty()) {
            val content: @Composable CarouselItemScope.(Int) -> Unit = { index ->
                val item = if (isInitialLoading) null else items[index]
                CarouselItem(
                    label = { CarouselItemDefaults.Text(item?.displayName.orEmpty()) },
                    Modifier.placeholder(item == null, shape = rememberMaskShape(CarouselItemDefaults.shape)),
                ) {
                    Surface(onClick = { item?.let(onClick) }) {
                        if (item == null) {
                            Box(Modifier.height(size.imageHeight).fillMaxWidth())
                        } else if (item.imageLarge.isBlank()) {
                            Box(Modifier.height(size.imageHeight).fillMaxWidth())
                        } else {
                            AsyncImage(
                                item.imageLarge,
                                modifier = Modifier.height(size.imageHeight),
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }

            HorizontalCenteredHeroCarousel(
                carouselState,
                Modifier.fillMaxWidth(),
                maxItemWidth = 300.dp,
                itemSpacing = itemSpacing,
                flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(
                    carouselState,
                    snapAnimationSpec = spring(stiffness = Spring.StiffnessMedium),
                ),
                content = content,
            )
            CarouselAutoAdvanceEffect(enabled = items.isNotEmpty() && !isHovered, carouselState)
        }

        if (error != null || (!isInitialLoading && items.isEmpty())) {
            Box(Modifier.height(size.imageHeight).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.minimumHairlineSize()) {
                    LoadErrorCard(
                        error ?: LoadError.NoResults,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

@TestOnly
val TestTodayUpdateSubjectInfos
    get() = listOf(
        TodayUpdateSubjectInfo(
            bangumiId = 467461,
            name = "Dandadan",
            nameCn = "膽大黨",
            imageLarge = "https://lain.bgm.tv/pic/cover/l/44/7d/467461_HHw4K.jpg",
        ),
        TodayUpdateSubjectInfo(
            bangumiId = 425998,
            name = "Re:ZERO",
            nameCn = "Re：從零開始的異世界生活 第三季",
            imageLarge = "https://lain.bgm.tv/pic/cover/l/26/d6/425998_dnzr8.jpg",
        ),
        TodayUpdateSubjectInfo(
            bangumiId = 389156,
            name = "Orb: On the Movements of the Earth",
            nameCn = "地。―關於地球的運動―",
            imageLarge = "https://lain.bgm.tv/pic/cover/l/5f/84/389156_J4gqQ.jpg",
        ),
        TodayUpdateSubjectInfo(
            bangumiId = 464376,
            name = "Makeine: Too Many Losing Heroines!",
            nameCn = "敗犬女主太多了！",
            imageLarge = "https://lain.bgm.tv/pic/cover/l/e4/dc/464376_NsZRw.jpg",
        ),
    )

@Composable
@PreviewSizeClasses
@PreviewLightDark
private fun PreviewTodayUpdatesCarousel() = ProvideCompositionLocalsForPreview {
    TodayUpdatesCarousel(
        items = TestTodayUpdateSubjectInfos,
        isInitialLoading = false,
        error = null,
        onRetry = {},
        onClick = {},
    )
}
