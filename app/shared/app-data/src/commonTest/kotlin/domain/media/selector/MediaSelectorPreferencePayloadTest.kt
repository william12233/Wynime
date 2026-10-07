package com.wynime.app.domain.media.selector

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.SOURCE_SECONDARY_WEB
import com.wynime.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnBeforeSelect
import com.wynime.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnChangePreference
import com.wynime.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnPreferWebSource
import com.wynime.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnSelect
import com.wynime.app.domain.media.selector.testFramework.SimpleMediaSelectorTestSuite
import com.wynime.app.domain.media.selector.testFramework.collectEvents
import com.wynime.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import com.wynime.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestContainer
class MediaSelectorPreferencePayloadTest {
    @Test
    fun `SEL-05 会话未选维度的载荷携带全局默认值`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(
            alliancePatterns = listOf("X"),
            showWithoutSubtitle = true,
        )
        preferenceApi.savedDefaultPreference.value = MediaPreference.PlatformDefault.copy(
            subtitleLanguageId = "CHS",
        )

        val collected = selector.collectEvents {
            selector.resolution.prefer("1080P")
        }

        assertEquals(1, collected.records.size)
        assertEquals(

            MediaPreference.Empty.copy(
                alliancePatterns = listOf("X"),
                showWithoutSubtitle = true,
                resolution = "1080P",
                subtitleLanguageId = "CHS",
            ),
            collected.onChangePreference.single().preference,
        )
    }

    @Test
    fun `SEL-05 载荷的四字段取 savedUserPreference 而非 savedDefaultPreference`() =
        runSimpleMediaSelectorTestSuite {
            preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(alliance = "DB组")
            preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(alliance = "默认组")

            val collected = selector.collectEvents {
                selector.resolution.prefer("1080P")
            }

            assertEquals(1, collected.records.size)
            assertEquals(

                MediaPreference.Empty.copy(
                    alliance = "DB组",
                    resolution = "1080P",
                ),
                collected.onChangePreference.single().preference,
            )
        }

    @Test
    fun `SEL-05 select 单字幕语言 media 后四字段全为 media 值`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(
            alliancePatterns = listOf("X"),
            showWithoutSubtitle = true,
        )
        preferenceApi.savedDefaultPreference.value = MediaPreference.PlatformDefault.copy(
            subtitleLanguageId = "CHS",
        )
        val target = media(
            sourceId = SOURCE_SECONDARY_WEB,
            alliance = "桜都字幕组",
            resolution = "720P",
            subtitleLanguages = listOf("CHT"),
        )
        mediaApi.addMedia(target)

        val collected = selector.collectEvents {
            selector.resolution.prefer("1080P")
            assertTrue(selector.select(target))
        }

        collected.assertOrder(
            OnChangePreference::class,
            OnBeforeSelect::class,
            OnChangePreference::class,
            OnPreferWebSource::class,
            OnSelect::class,
        )
        assertEquals(
            listOf(
                MediaPreference.Empty.copy(
                    alliancePatterns = listOf("X"),
                    showWithoutSubtitle = true,
                    resolution = "1080P",
                    subtitleLanguageId = "CHS",
                ),
                MediaPreference.Empty.copy(
                    alliancePatterns = listOf("X"),
                    showWithoutSubtitle = true,
                    alliance = "桜都字幕组",
                    resolution = "720P",
                    subtitleLanguageId = "CHT",
                    mediaSourceId = SOURCE_SECONDARY_WEB,
                ),
            ),
            collected.onChangePreference.map { it.preference },
        )
    }

    private fun SimpleMediaSelectorTestSuite.createSelectorCountingSavedUserPreference(
        collectCount: MutableStateFlow<Int>,
    ): DefaultMediaSelector = DefaultMediaSelector(
        mediaSelectorContextNotCached = preferenceApi.mediaSelectorContext,
        mediaListNotCached = mediaApi.mediaList,
        savedUserPreference = flow {
            collectCount.update { it + 1 }
            emit(preferenceApi.savedUserPreference.value)
        },
        savedDefaultPreference = preferenceApi.savedDefaultPreference,
        mediaSelectorSettings = preferenceApi.mediaSelectorSettings,
        flowCoroutineContext = Dispatchers.Default,
        enableCaching = false,
        cachingScope = testScope.backgroundScope,
    )

    @Test
    fun `SAVE-02 无订阅者时 select 跳过 onChangePreference 的计算`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
        val savedUserCollectCount = MutableStateFlow(0)
        val spySelector = createSelectorCountingSavedUserPreference(savedUserCollectCount)
        val target = media(alliance = "字幕组", subtitleLanguages = listOf("CHS"))
        mediaApi.addMedia(target)

        assertTrue(spySelector.select(target))
        assertEquals(0, savedUserCollectCount.value, "无订阅者时不应读取 savedUserPreference, 即不应计算载荷")

        val collected = spySelector.collectEvents {
            spySelector.alliance.prefer("另一个字幕组")
        }
        assertTrue(
            savedUserCollectCount.value > 0,
            "有订阅者时应读取 savedUserPreference 以计算载荷, 实际读取次数=${savedUserCollectCount.value}",
        )
        assertEquals(
            listOf(
                MediaPreference.Empty.copy(
                    alliance = "另一个字幕组",
                    resolution = target.properties.resolution,
                    subtitleLanguageId = "CHS",
                    mediaSourceId = target.mediaSourceId,
                ),
            ),
            collected.onChangePreference.map { it.preference },
        )
    }

    @Test
    fun `EVT-01 晚订阅者收不到 select 期间的事件 且 ITEM-02 会话 override 跨调用保留`() =
        runSimpleMediaSelectorTestSuite {
            preferenceApi.savedUserPreference.value = MediaPreference.Empty
            preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
            val target = media(alliance = "字幕组", subtitleLanguages = listOf("CHS"))
            mediaApi.addMedia(target)

            assertTrue(selector.select(target))

            val collected = selector.collectEvents {
                selector.alliance.prefer("另一个字幕组")
            }

            collected.assertOrder(OnChangePreference::class)
            assertEquals(
                listOf(

                    MediaPreference.Empty.copy(
                        alliance = "另一个字幕组",
                        resolution = target.properties.resolution,
                        subtitleLanguageId = "CHS",
                        mediaSourceId = target.mediaSourceId,
                    ),
                ),
                collected.onChangePreference.map { it.preference },
            )
        }

    @Test
    fun `SAVE-02 订阅存在时 select 广播一次偏好事件`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
        val target = media(alliance = "字幕组", subtitleLanguages = listOf("CHS"))
        mediaApi.addMedia(target)

        val collected = selector.collectEvents {
            assertTrue(selector.select(target))
        }

        collected.assertOrder(
            OnBeforeSelect::class,
            OnChangePreference::class,
            OnPreferWebSource::class,
            OnSelect::class,
        )
        assertEquals(
            listOf(
                MediaPreference.Empty.copy(
                    alliance = "字幕组",
                    resolution = target.properties.resolution,
                    subtitleLanguageId = "CHS",
                    mediaSourceId = target.mediaSourceId,
                ),
            ),
            collected.onChangePreference.map { it.preference },
        )
    }
}
