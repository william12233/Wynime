package com.wynime.app.domain.media.selector

import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnBeforeSelect
import com.wynime.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnChangePreference
import com.wynime.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnPreferWebSource
import com.wynime.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnSelect
import com.wynime.app.domain.media.selector.testFramework.SimpleMediaSelectorTestSuite
import com.wynime.app.domain.media.selector.testFramework.collectEvents
import com.wynime.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestContainer
class MediaSelectorEventOrderTest {

    private suspend fun SimpleMediaSelectorTestSuite.checkSelectWebMediaSideEffectOrder() {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
        preferenceApi.mediaSelectorContext.value = preferenceApi.mediaSelectorContext.value.copy(
            subjectInfo = SubjectInfo.Empty.copy(subjectId = 123),
        )
        val previous = media(alliance = "字幕组A", subtitleLanguages = listOf("CHS"))
        val target = media(alliance = "字幕组B", subtitleLanguages = listOf("CHS"), kind = MediaSourceKind.WEB)
        mediaApi.addMedia(previous, target)
        assertTrue(selector.select(previous))

        val collected = selector.collectEvents {
            assertTrue(selector.select(target))
        }

        collected.assertOrder(
            OnBeforeSelect::class,
            OnChangePreference::class,
            OnPreferWebSource::class,
            OnSelect::class,
        )
        val expectedEvent = SelectEvent(
            media = target,
            subtitleLanguageId = null,
            previousMedia = previous,
        )
        assertEquals(expectedEvent, collected.onBeforeSelect.single().event)
        assertEquals(expectedEvent, collected.onSelect.single().event)
        assertEquals(
            PreferWebSourceEvent(subjectId = 123, mediaSourceId = target.mediaSourceId),
            collected.onPreferWebSource.single().event,
        )

        assertEquals(previous, collected.onBeforeSelect.single().selectedAtEmit)
        assertEquals(target, collected.onChangePreference.single().selectedAtEmit)
        assertEquals(target, collected.onPreferWebSource.single().selectedAtEmit)
        assertEquals(target, collected.onSelect.single().selectedAtEmit)
    }

    @Test
    fun `SEL-02 select WEB media 副作用顺序为 onBeforeSelect onChangePreference onPreferWebSource onSelect`() =
        runSimpleMediaSelectorTestSuite {
            checkSelectWebMediaSideEffectOrder()
        }

    @Test
    fun `SEL-02 cachingEnabled 时 select WEB media 副作用顺序不变`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = true) {
            checkSelectWebMediaSideEffectOrder()
        }

    @Test
    fun `SEL-02 select local cache media 不发 onPreferWebSource`() = runSimpleMediaSelectorTestSuite {
        val previous = media(alliance = "字幕组A", subtitleLanguages = listOf("CHS"))
        val target = media(
            alliance = "字幕组B",
            subtitleLanguages = listOf("CHS"),
            kind = MediaSourceKind.LocalCache,
        )
        mediaApi.addMedia(previous, target)
        assertTrue(selector.select(previous))

        val collected = selector.collectEvents {
            assertTrue(selector.select(target))
        }

        collected.assertOrder(
            OnBeforeSelect::class,
            OnChangePreference::class,
            OnSelect::class,
        )
        val expectedEvent = SelectEvent(
            media = target,
            subtitleLanguageId = null,
            previousMedia = previous,
        )
        assertEquals(expectedEvent, collected.onBeforeSelect.single().event)
        assertEquals(expectedEvent, collected.onSelect.single().event)

        assertEquals(previous, collected.onBeforeSelect.single().selectedAtEmit)
        assertEquals(target, collected.onChangePreference.single().selectedAtEmit)
        assertEquals(target, collected.onSelect.single().selectedAtEmit)
    }

    @Test
    fun `EVT-01 重复 select 同一 media 返回 false 且零事件`() = runSimpleMediaSelectorTestSuite {
        val target = media(alliance = "字幕组", subtitleLanguages = listOf("CHS"))
        mediaApi.addMedia(target)

        selector.collectEvents {
            assertTrue(selector.select(target))
        }.run {
            assertEquals(1, onBeforeSelect.size)
            assertEquals(1, onSelect.size)
        }

        selector.collectEvents {
            assertFalse(selector.select(target))
        }.expectNoEvents()
    }
}
