package com.wynime.app.domain.player

import app.cash.turbine.test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.bytes
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.features.NetworkStats
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalMediampApi::class)
class PlayerDownloadSpeedTest {
    private fun TestScope.createPlayer(): TestMediampPlayer =
        TestMediampPlayer(StandardTestDispatcher(testScheduler))

    private val period = PLAYER_DOWNLOAD_SPEED_SAMPLE_PERIOD

    @Test
    fun `no media is unspecified`() = runTest {
        val player = createPlayer()
        player.downloadSpeedFlow().test {
            advanceUntilIdle()
            assertEquals(FileSize.Unspecified, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `uri media without a known speed is unspecified`() = runTest {
        val player = createPlayer()
        player.setMediaData(UriMediaData("https://example.com/video.m3u8"))
        advanceUntilIdle()

        player.downloadSpeedFlow().test {
            runCurrent()
            assertEquals(FileSize.Unspecified, awaitItem())
            advanceTimeBy(period * 3)
            expectNoEvents()
        }
    }

    @Test
    fun `uri media follows the network stats of the player`() = runTest {
        val player = createPlayer()
        player.setMediaData(UriMediaData("https://example.com/video.m3u8"))
        advanceUntilIdle()

        player.downloadSpeedFlow().test {
            runCurrent()
            assertEquals(FileSize.Unspecified, awaitItem())

            player.injectDownloadSpeed(1_500_000L)
            advanceTimeBy(period)
            assertEquals(1_500_000L.bytes, awaitItem())

            player.injectDownloadSpeed(0L)
            advanceTimeBy(period)
            assertEquals(FileSize.Zero, awaitItem())

            player.injectDownloadSpeed(NetworkStats.UNKNOWN_SPEED)
            advanceTimeBy(period)
            assertEquals(FileSize.Unspecified, awaitItem())
        }
    }

    @Test
    fun `uri media speed is sampled once per period`() = runTest {
        val player = createPlayer()
        player.setMediaData(UriMediaData("https://example.com/video.m3u8"))
        advanceUntilIdle()

        player.downloadSpeedFlow().test {
            runCurrent()
            assertEquals(FileSize.Unspecified, awaitItem())

            player.injectDownloadSpeed(100L)
            runCurrent()
            player.injectDownloadSpeed(200L)
            advanceTimeBy(period / 2)
            player.injectDownloadSpeed(300L)
            expectNoEvents()

            advanceTimeBy(period / 2)
            assertEquals(300L.bytes, awaitItem())

            advanceTimeBy(period * 3)
            expectNoEvents()
        }
    }

    @Test
    fun `speed becomes unspecified again when media is reopened`() = runTest {
        val player = createPlayer()
        player.setMediaData(UriMediaData("https://example.com/first.m3u8"))
        advanceUntilIdle()

        player.downloadSpeedFlow().test {
            runCurrent()
            assertEquals(FileSize.Unspecified, awaitItem())
            player.injectDownloadSpeed(1_000L)
            advanceTimeBy(period)
            assertEquals(1_000L.bytes, awaitItem())

            player.setMediaData(UriMediaData("https://example.com/second.m3u8"))
            advanceTimeBy(period)

            assertEquals(FileSize.Unspecified, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
