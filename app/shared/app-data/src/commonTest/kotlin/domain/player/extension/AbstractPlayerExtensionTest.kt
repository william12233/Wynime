package com.wynime.app.domain.player.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import com.wynime.app.domain.episode.EpisodeFetchSelectPlayState
import com.wynime.app.domain.episode.EpisodePlayerTestSuite
import org.openani.mediamp.isMediaLoaded
import org.openani.mediamp.metadata.MediaProperties
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class AbstractPlayerExtensionTest {
    protected val subjectId = 1
    protected val initialEpisodeId = 2

    fun EpisodePlayerTestSuite.createState(
        extensions: List<EpisodePlayerExtensionFactory<*>> = listOf(),
    ): EpisodeFetchSelectPlayState {
        return EpisodeFetchSelectPlayState(
            subjectId,
            initialEpisodeId,
            player,
            backgroundScope,
            extensions = extensions,
            koin,
            mainDispatcher = EmptyCoroutineContext,
        )
    }

    fun EpisodePlayerTestSuite.setMediaDuration(durationMillis: Long?) {
        val properties = (player.mediaProperties.value ?: player.defaultMediaProperties)
            .copy(durationMillis = durationMillis)
        player.defaultMediaProperties = properties
        if (player.state.value.isMediaLoaded) {
            player.injectProperties(properties)
        }
    }

    @BeforeTest
    fun installDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun resetDispatcher() {
        Dispatchers.resetMain()
    }
}

suspend fun TestMediampPlayer.loadMedia(
    durationMs: Long?,
    playWhenReady: Boolean = false,
    uri: String = "file://test.mp4",
    startPositionMillis: Long = 0L,
) {
    defaultMediaProperties = defaultMediaProperties.copy(durationMillis = durationMs)
    setMediaData(UriMediaData(uri), playWhenReady = playWhenReady, startPositionMillis = startPositionMillis)
}
