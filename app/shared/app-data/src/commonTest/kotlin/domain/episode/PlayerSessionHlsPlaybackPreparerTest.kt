package com.wynime.app.domain.episode

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.domain.media.TestMediaList
import com.wynime.app.domain.media.hls.HlsPlaybackOptions
import com.wynime.app.domain.media.hls.HlsPlaybackPreparer
import com.wynime.app.domain.media.hls.HlsPlaybackPreparerResult
import com.wynime.app.domain.media.hls.HlsPlaybackProxySession
import com.wynime.app.domain.media.player.data.MediaDataProvider
import com.wynime.app.domain.media.resolver.EpisodeMetadata
import com.wynime.app.domain.media.resolver.MediaResolver
import com.wynime.app.domain.settings.GetVideoScaffoldConfigUseCase
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.Media
import org.koin.core.Koin
import org.koin.dsl.module
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlayerSessionHlsPlaybackPreparerTest {
    private val episode = EpisodeMetadata(title = "EP1", ep = EpisodeSort(1), sort = EpisodeSort(1))

    @Test
    fun `does not prepare uri media data when setting is disabled`() = runTest {
        val preparer = RecordingHlsPlaybackPreparer()
        val playerSession = createPlayerSession(
            hlsEnabled = false,
            preparer = preparer,
        )

        playerSession.loadMedia(TestMediaList.first(), episode)

        val data = assertIs<UriMediaData>(playerSession.player.mediaData.first())
        assertEquals("https://example.com/original.m3u8", data.uri)
        assertEquals(0, preparer.prepareCount)
    }

    @Test
    fun `prepares uri media data when setting is enabled`() = runTest {
        val preparer = RecordingHlsPlaybackPreparer()
        val playerSession = createPlayerSession(
            hlsEnabled = true,
            preparer = preparer,
        )

        playerSession.loadMedia(TestMediaList.first(), episode)

        val data = assertIs<UriMediaData>(playerSession.player.mediaData.first())
        assertEquals("http://127.0.0.1:18080/proxied.m3u8", data.uri)
        assertEquals(1, preparer.prepareCount)
        assertFalse(preparer.sessions.single().closed)
    }

    @Test
    fun `closes prepared proxy session on next load and player session close`() = runTest {
        val preparer = RecordingHlsPlaybackPreparer()
        val playerSession = createPlayerSession(
            hlsEnabled = true,
            preparer = preparer,
        )

        playerSession.loadMedia(TestMediaList.first(), episode)
        val firstSession = preparer.sessions.single()

        playerSession.loadMedia(TestMediaList.first(), episode)
        val secondSession = preparer.sessions.last()

        assertTrue(firstSession.closed)
        assertFalse(secondSession.closed)

        playerSession.close()

        assertTrue(secondSession.closed)
    }

    @Test
    fun `closes prepared proxy session on stop playback`() = runTest {
        val preparer = RecordingHlsPlaybackPreparer()
        val playerSession = createPlayerSession(
            hlsEnabled = true,
            preparer = preparer,
        )

        playerSession.loadMedia(TestMediaList.first(), episode)
        val session = preparer.sessions.single()

        playerSession.stopPlayback()

        assertTrue(session.closed)
    }

    private fun TestScope.createPlayerSession(
        hlsEnabled: Boolean,
        preparer: HlsPlaybackPreparer,
    ): PlayerSession {
        val koin = Koin()
        koin.loadModules(
            listOf(
                module {
                    single<MediaResolver> {
                        StaticMediaResolver(
                            UriMediaData(
                                "https://example.com/original.m3u8",
                                mapOf("User-Agent" to "WynimeTest"),
                            ),
                        )
                    }
                    single<GetVideoScaffoldConfigUseCase> {
                        GetVideoScaffoldConfigUseCase {
                            flowOf(
                                VideoScaffoldConfig.AllDisabled.copy(
                                    enableExperimentalHlsSegmentFiltering = hlsEnabled,
                                ),
                            )
                        }
                    }
                    single<HlsPlaybackPreparer> { preparer }
                },
            ),
        )
        val player = TestMediampPlayer(StandardTestDispatcher(testScheduler))
        return PlayerSession(player, koin, backgroundScope, mainDispatcher = EmptyCoroutineContext)
    }

    private class StaticMediaResolver(
        private val data: UriMediaData,
    ) : MediaResolver {
        override fun supports(media: Media): Boolean = true

        override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
            return object : MediaDataProvider<UriMediaData> {
                override val extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY

                override suspend fun open(scopeForCleanup: CoroutineScope): UriMediaData {
                    return data
                }
            }
        }
    }

    private class RecordingHlsPlaybackPreparer : HlsPlaybackPreparer {
        val sessions = mutableListOf<RecordingHlsPlaybackProxySession>()
        var prepareCount: Int = 0
            private set

        override suspend fun prepare(data: UriMediaData, options: HlsPlaybackOptions): HlsPlaybackPreparerResult {
            prepareCount++
            val session = RecordingHlsPlaybackProxySession()
            sessions += session
            return HlsPlaybackPreparerResult(
                data = UriMediaData(
                    "http://127.0.0.1:18080/proxied.m3u8",
                    data.headers,
                    data.extraFiles,
                ),
                session = session,
            )
        }
    }

    private class RecordingHlsPlaybackProxySession : HlsPlaybackProxySession {
        var closed: Boolean = false
            private set

        override fun close() {
            closed = true
        }
    }
}
