package com.wynime.app.domain.media.selector

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import com.wynime.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import com.wynime.app.domain.player.extension.PlayerLoadErrorHandler
import com.wynime.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertTrue

@TestContainer
class MediaSelectorManualSelectTest {
    @Test
    fun `blacklist is updated on manual select`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("ゆるキャン△")
            val firstMedia = media(
                mediaId = "ゆるキャン△-a",
                alliance = "A",
                subtitleLanguages = listOf("CHS"),
            )
            val secondMedia = media(
                mediaId = "ゆるキャン△-b",
                alliance = "B",
                subtitleLanguages = listOf("CHT"),
            )
            mediaApi.addMedia(firstMedia)
            mediaApi.addMedia(secondMedia)
        },
    ) {
        val first = mediaApi.mediaList.value[0]
        val second = mediaApi.mediaList.value[1]

        val handler = PlayerLoadErrorHandler(
            getPreferKind = { null },
            getSourceTiers = { MediaSelectorSourceTiers(emptyMap()) },
        )

        coroutineScope {
            val job = launch {
                handler.observeMediaSelectorBlacklist(
                    mediaSelectorFlow = flowOf(selector),
                )
            }

            testScope.runCurrent()
            selector.select(first)
            selector.select(second)
            testScope.advanceUntilIdle()

            assertTrue(
                actual = "ゆるキャン△-a" in handler.blacklist,
                message = "Previous media should be blacklisted",
            )

            job.cancel()
        }

    }
}