package com.wynime.app.domain.media.selector

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.SOURCE_PRIMARY_WEB
import com.wynime.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.SOURCE_SECONDARY_WEB
import com.wynime.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import com.wynime.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestContainer
class MediaSelectorEventHandlersTest {
    @Test
    fun `SAVE-03 debounce 999ms 内无保存 满 1000ms 恰保存一次且载荷四字段来自所选 media`() = runSimpleMediaSelectorTestSuite(
        cachingEnabled = true,
    ) {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
        val target = media(
            sourceId = SOURCE_SECONDARY_WEB,
            alliance = "桜都字幕组",
            resolution = "720P",
            subtitleLanguages = listOf("CHT"),
        )
        mediaApi.addMedia(target)

        val saved = mutableListOf<MediaPreference>()
        testScope.backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            selector.eventHandling.savePreferenceOnSelect { saved.add(it) }
        }
        selector.events.onChangePreference.subscriptionCount.first { it > 0 }

        assertTrue(selector.select(target))
        testScope.runCurrent()

        testScope.advanceTimeBy(999)
        testScope.runCurrent()
        assertEquals(emptyList(), saved)

        testScope.advanceTimeBy(1)
        testScope.runCurrent()
        assertEquals(
            listOf(
                MediaPreference.Empty.copy(
                    alliance = "桜都字幕组",
                    resolution = "720P",
                    subtitleLanguageId = "CHT",
                    mediaSourceId = SOURCE_SECONDARY_WEB,
                ),
            ),
            saved,
        )

        testScope.advanceUntilIdle()
        assertEquals(1, saved.size)
    }

    @Test
    fun `SAVE-03 1s 窗口内连续 select A B 只保存 B`() = runSimpleMediaSelectorTestSuite(
        cachingEnabled = true,
    ) {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
        val mediaA = media(
            sourceId = SOURCE_PRIMARY_WEB,
            alliance = "字幕组A",
            resolution = "1080P",
            subtitleLanguages = listOf("CHS"),
        )
        val mediaB = media(
            sourceId = SOURCE_SECONDARY_WEB,
            alliance = "字幕组B",
            resolution = "720P",
            subtitleLanguages = listOf("CHT"),
        )
        mediaApi.addMedia(mediaA, mediaB)

        val saved = mutableListOf<MediaPreference>()
        testScope.backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            selector.eventHandling.savePreferenceOnSelect { saved.add(it) }
        }
        selector.events.onChangePreference.subscriptionCount.first { it > 0 }

        assertTrue(selector.select(mediaA))
        testScope.runCurrent()
        testScope.advanceTimeBy(500)

        assertTrue(selector.select(mediaB))
        testScope.runCurrent()

        testScope.advanceTimeBy(999)
        testScope.runCurrent()
        assertEquals(emptyList(), saved)

        testScope.advanceTimeBy(1)
        testScope.runCurrent()
        assertEquals(
            listOf(
                MediaPreference.Empty.copy(
                    alliance = "字幕组B",
                    resolution = "720P",
                    subtitleLanguageId = "CHT",
                    mediaSourceId = SOURCE_SECONDARY_WEB,
                ),
            ),
            saved,
        )

        testScope.advanceUntilIdle()
        assertEquals(1, saved.size)
    }

    @Test
    fun `SAVE-03 debounce 期间取消收集 job 该次变更不落盘`() = runSimpleMediaSelectorTestSuite(
        cachingEnabled = true,
    ) {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
        val target = media(
            sourceId = SOURCE_SECONDARY_WEB,
            alliance = "桜都字幕组",
            resolution = "720P",
            subtitleLanguages = listOf("CHT"),
        )
        mediaApi.addMedia(target)

        val saved = mutableListOf<MediaPreference>()
        val job = testScope.backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            selector.eventHandling.savePreferenceOnSelect { saved.add(it) }
        }
        selector.events.onChangePreference.subscriptionCount.first { it > 0 }

        assertTrue(selector.select(target))
        testScope.runCurrent()
        testScope.advanceTimeBy(500)

        job.cancel()
        testScope.advanceUntilIdle()
        assertEquals(emptyList(), saved)
    }
}
