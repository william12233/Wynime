package com.wynime.app.ui.download.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.wynime.app.domain.media.cache.MediaCache
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme
import com.wynime.app.ui.foundation.interaction.WindowDragArea
import com.wynime.app.ui.foundation.theme.WynimeThemeDefaults
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.cache_details_title
import com.wynime.datasources.api.source.MediaSourceInfo
import com.wynime.utils.coroutines.sampleWithInitial
import com.wynime.utils.logging.logger
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class MediaCacheDetailsPageViewModel(
    private val cacheId: String,
) : AbstractViewModel(), KoinComponent {
    private val downloadManager: MediaDownloadManager by inject()
    private val mediaSourceManager: MediaSourceManager by inject()

    private val mediaCacheFlow = downloadManager.downloads
        .map { list -> list.firstOrNull { it.id == cacheId }?.cache }
        .shareInBackground()

    private val sourceInfoFlow
        get() = mediaCacheFlow
            .map { it?.origin }
            .flatMapLatest { media ->
                media?.mediaSourceId?.let { mediaSourceManager.infoFlowByMediaSourceId(it) } ?: flowOf(null)
            }

    private val downloaderFlow = mediaCacheFlow.flatMapLatest { cache ->
        if (cache == null) return@flatMapLatest flowOf(null)
        combine(cache.state, cache.sessionStats, cache.downloaderStatus) { state, stats, status ->
            DownloaderDetails(state, stats, status)
        }.sampleWithInitial(1.seconds)
    }

    val screenStateFlow =
        combine(sourceInfoFlow, mediaCacheFlow) { sourceInfo, mediaCache ->
            createMediaCacheDetailsScreenState(mediaCache, sourceInfo)
        }.combine(downloaderFlow) { state, downloader -> state.copy(downloader = downloader) }
            .stateInBackground(MediaCacheDetailsScreenState(null))
}

internal suspend fun createMediaCacheDetailsScreenState(
    mediaCache: MediaCache?,
    sourceInfo: MediaSourceInfo?,
): MediaCacheDetailsScreenState {
    val originalMedia = mediaCache?.origin ?: return MediaCacheDetailsScreenState(details = null)
    val cachedMedia = runCatching { mediaCache.getCachedMedia() }.getOrNull()
    return MediaCacheDetailsScreenState(MediaDetails.from(originalMedia, sourceInfo, cachedMedia))
}

data class MediaCacheDetailsScreenState(
    val details: MediaDetails?,
    val downloader: DownloaderDetails? = null,
)

@Composable
fun MediaCacheDetailsScreen(
    vm: MediaCacheDetailsPageViewModel,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    val screenState by vm.screenStateFlow.collectAsStateWithLifecycle()
    MediaCacheDetailsScreen(
        state = screenState,
        navigationIcon = navigationIcon,
        modifier = modifier,
        windowInsets = windowInsets,
    )
}

private val logger = logger<MediaCacheDetailsPageViewModel>()

@Composable
fun MediaCacheDetailsScreen(
    state: MediaCacheDetailsScreenState,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            WindowDragArea {
                TopAppBar(
                    title = { Text(stringResource(Lang.cache_details_title)) },
                    navigationIcon = navigationIcon,
                    colors = WynimeThemeDefaults.topAppBarColors(),
                    windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                )
            }
        },
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .wrapContentWidth(align = Alignment.CenterHorizontally)
                .padding(paddingValues)
                .widthIn(max = 1200.dp),
        ) {
            WynimeAnimatedVisibility(
                visible = state.details != null,
                enter = LocalWynimeMotionScheme.current.animatedVisibility.screenEnter,
                exit = LocalWynimeMotionScheme.current.animatedVisibility.screenExit,
            ) {
                Surface(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(vertical = 16.dp),
                    color = ListItemDefaults.containerColor,
                ) {
                    state.details?.let {
                        MediaDetailsLazyGrid(
                            it,
                            Modifier.fillMaxHeight(),
                            downloader = state.downloader,
                        )
                    }
                }
            }
        }
    }
}
