package com.wynime.app.domain.media.selector

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.domain.media.selector.testFramework.FetchMediaSelectorTestSuite
import com.wynime.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.DEFAULT_PREFERENCE
import com.wynime.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.SOURCE_PRIMARY_WEB
import com.wynime.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnBeforeSelect
import com.wynime.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnChangePreference
import com.wynime.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnPreferWebSource
import com.wynime.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnSelect
import com.wynime.app.domain.media.selector.testFramework.SimpleMediaSelectorTestSuite
import com.wynime.app.domain.media.selector.testFramework.collectEvents
import com.wynime.app.domain.media.selector.testFramework.runFetchMediaSelectorTestSuite
import com.wynime.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import com.wynime.app.domain.media.selector.testFramework.tier
import com.wynime.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import com.wynime.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import com.wynime.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import com.wynime.datasources.api.source.MediaSourceKind.WEB
import com.wynime.test.DisabledOnNative
import org.koin.core.Koin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@DisabledOnNative
class MediaSelectorCachingEnabledTest {

    private suspend fun SimpleMediaSelectorTestSuite.checkTrySelectDefaultPicksPreferredResolution() {
        initSubject("孤独摇滚")
        mediaApi.addMedia(
            media(
                kind = WEB, subjectName = initApi.subjectName,
                alliance = "字幕组A", resolution = "1080P", subtitleLanguages = listOf("CHS"),
            ),
            media(
                kind = WEB, subjectName = initApi.subjectName,
                alliance = "字幕组B", resolution = "720P", subtitleLanguages = listOf("CHT"),
            ),
        )

        val selected = selector.trySelectDefault()
        assertNotNull(selected)
        assertEquals("1080P", selected.properties.resolution)
        assertEquals(selected, selector.selected.value)

        assertNull(selector.trySelectDefault())
    }

    @Test
    fun `TRY-01 trySelectDefault 主分支选中偏好资源 - cachingEnabled 为 false`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = false) {
            checkTrySelectDefaultPicksPreferredResolution()
        }

    @Test
    fun `TRY-01 trySelectDefault 主分支选中偏好资源 - cachingEnabled 为 true`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = true) {
            checkTrySelectDefaultPicksPreferredResolution()
        }

    private suspend fun SimpleMediaSelectorTestSuite.checkPreferredCandidatesFilteredByUserPreference() {
        initSubject("孤独摇滚")
        preferenceApi.savedUserPreference.value = DEFAULT_PREFERENCE.copy(alliance = "字幕组A")
        mediaApi.addMedia(
            media(kind = WEB, subjectName = initApi.subjectName, alliance = "字幕组A"),
            media(kind = WEB, subjectName = initApi.subjectName, alliance = "字幕组B"),
        )

        assertEquals(
            setOf("字幕组A", "字幕组B"),
            selector.filteredCandidatesMedia.first().map { it.properties.alliance }.toSet(),
        )
        assertEquals(
            listOf("字幕组A"),
            selector.preferredCandidatesMedia.first().map { it.properties.alliance },
        )
    }

    @Test
    fun `PF-02 preferredCandidates 按用户偏好过滤而 filteredCandidates 不过滤 - cachingEnabled 为 false`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = false) {
            checkPreferredCandidatesFilteredByUserPreference()
        }

    @Test
    fun `PF-02 preferredCandidates 按用户偏好过滤而 filteredCandidates 不过滤 - cachingEnabled 为 true`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = true) {
            checkPreferredCandidatesFilteredByUserPreference()
        }

    private suspend fun SimpleMediaSelectorTestSuite.checkSelectConvergesToSelectedMedia() {
        initSubject("孤独摇滚")
        mediaApi.addMedia(
            media(
                kind = WEB, subjectName = initApi.subjectName,
                alliance = "字幕组A", subtitleLanguages = listOf("CHS"),
            ),
            media(
                kind = WEB, subjectName = initApi.subjectName,
                alliance = "字幕组B", subtitleLanguages = listOf("CHT"),
            ),
        )
        val mediaB = mediaApi.mediaList.value[1]
        assertEquals(2, selector.preferredCandidatesMedia.first().size)

        assertTrue(selector.select(mediaB))

        testScope.runCurrent()
        assertEquals(listOf(mediaB), selector.preferredCandidatesMedia.first())
    }

    @Test
    fun `INFRA-01 select 后候选流收敛到被选中的 media - cachingEnabled 为 false`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = false) {
            checkSelectConvergesToSelectedMedia()
        }

    @Test
    fun `INFRA-01 select 后候选流收敛到被选中的 media - cachingEnabled 为 true`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = true) {
            checkSelectConvergesToSelectedMedia()
        }

    @Test
    fun `INFRA-01 缓存路径下上游变更在调度器被推进前仍回放旧快照`() = runSimpleMediaSelectorTestSuite(
        cachingEnabled = true,
        buildTest = {
            initSubject("孤独摇滚")
            preferenceApi.savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(showWithoutSubtitle = true)
            mediaApi.addMedia(
                media(
                    kind = WEB, subjectName = initApi.subjectName,
                    alliance = "字幕组A", subtitleLanguages = listOf("CHS"),
                ),
                media(
                    kind = WEB, subjectName = initApi.subjectName,
                    alliance = "字幕组B", subtitleLanguages = emptyList(),
                ),
            )
        },
    ) {

        assertEquals(2, selector.preferredCandidatesMedia.first().size)

        preferenceApi.savedDefaultPreference.value = DEFAULT_PREFERENCE.copy(showWithoutSubtitle = false)
        assertEquals(2, selector.preferredCandidatesMedia.first().size)

        testScope.runCurrent()
        assertEquals(1, selector.preferredCandidatesMedia.first().size)
    }

    private suspend fun SimpleMediaSelectorTestSuite.checkSelectBroadcastsEventsAndPayload() {
        initSubject("孤独摇滚")
        mediaApi.addMedia(
            media(
                kind = WEB, subjectName = initApi.subjectName,
                alliance = "字幕组A", resolution = "1080P", subtitleLanguages = listOf("CHS"),
            ),
            media(
                kind = WEB, subjectName = initApi.subjectName,
                alliance = "字幕组B", resolution = "720P", subtitleLanguages = listOf("CHT"),
            ),
        )
        val mediaA = mediaApi.mediaList.value[0]

        val collected = selector.collectEvents {
            assertTrue(selector.select(mediaA))
        }

        collected.assertOrder(
            OnBeforeSelect::class,
            OnChangePreference::class,
            OnPreferWebSource::class,
            OnSelect::class,
        )
        val expectedEvent = SelectEvent(media = mediaA, subtitleLanguageId = null, previousMedia = null)
        assertEquals(expectedEvent, collected.onBeforeSelect.single().event)
        assertEquals(expectedEvent, collected.onSelect.single().event)

        assertEquals(
            DEFAULT_PREFERENCE.copy(
                alliance = "字幕组A",
                resolution = "1080P",
                subtitleLanguageId = "CHS",
                mediaSourceId = SOURCE_PRIMARY_WEB,
            ),
            collected.onChangePreference.single().preference,
        )
    }

    @Test
    fun `SEL-05 select 广播的偏好载荷四字段来自 media - cachingEnabled 为 false`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = false) {
            checkSelectBroadcastsEventsAndPayload()
        }

    @Test
    fun `SEL-05 select 广播的偏好载荷四字段来自 media - cachingEnabled 为 true`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = true) {
            checkSelectBroadcastsEventsAndPayload()
        }

    private val preferredWebMediaSource = MutableStateFlow<String?>(null)

    context(scope: TestScope)
    private suspend fun FetchMediaSelectorTestSuite.checkTier0SourceIsInstantlySelected() {
        initSubject("test")
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        preferenceApi.mediaSelectorSettings.value = MediaSelectorSettings.AllVisible.copy(
            autoEnableLastSelected = false,
            fastSelectWebKind = true,
            preferKind = WEB,
            fastSelectWebLowTierToleranceDuration = 5.seconds,
        )

        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 0 }
            }
        }

        val useCase = MediaSelectorAutoSelectUseCaseImpl(createKoin())
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            useCase(session, selector)
        }
        sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
        scope.runCurrent()

        assertEquals("web1", selector.selected.value?.mediaSourceId)
        assertTrue(job.isCompleted)
        assertFalse(job.isCancelled)
    }

    @Test
    fun `FAST-02 tier0 源被秒选并结束编排 - cachingEnabled 为 false`() =
        runFetchMediaSelectorTestSuite(cachingEnabled = false) {
            checkTier0SourceIsInstantlySelected()
        }

    @Test
    fun `FAST-02 tier0 源被秒选并结束编排 - cachingEnabled 为 true`() =
        runFetchMediaSelectorTestSuite(cachingEnabled = true) {
            checkTier0SourceIsInstantlySelected()
        }

    private fun FetchMediaSelectorTestSuite.createKoin(): Koin {
        return Koin().apply {
            loadModules(
                listOf(
                    module {
                        single<GetMediaSelectorSettingsFlowUseCase> {
                            GetMediaSelectorSettingsFlowUseCase { preferenceApi.mediaSelectorSettings }
                        }
                        single<GetMediaSelectorSourceTiersUseCase> {
                            GetMediaSelectorSourceTiersUseCase {
                                preferenceApi.mediaSelectorContext.map {
                                    it.mediaSourceTiers ?: MediaSelectorSourceTiers.Empty
                                }
                            }
                        }
                        single<GetPreferredWebMediaSourceUseCase> {
                            GetPreferredWebMediaSourceUseCase { preferredWebMediaSource }
                        }
                    },
                ),
            )
        }
    }
}
