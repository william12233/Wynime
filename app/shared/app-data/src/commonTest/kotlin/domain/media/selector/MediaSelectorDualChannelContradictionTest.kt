package com.wynime.app.domain.media.selector

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.media.selector.testFramework.FetchMediaSelectorTestSuite
import com.wynime.app.domain.media.selector.testFramework.Handle
import com.wynime.app.domain.media.selector.testFramework.MediaSelectorTestSuite
import com.wynime.app.domain.media.selector.testFramework.runFetchMediaSelectorTestSuite
import com.wynime.app.domain.media.selector.testFramework.tier
import com.wynime.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import com.wynime.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import com.wynime.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceKind.WEB
import com.wynime.test.DisabledOnNative
import org.koin.core.Koin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@DisabledOnNative
class MediaSelectorDualChannelContradictionTest {
    private val preferredWebMediaSource = MutableStateFlow<String?>(null)

    @Test
    fun `MIG-DUAL-00 正向对照 JSON无源约束时Room偏好源在T0经clause1直接选中`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings()

        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 0 }
                val web2 by web { tier = 2 }
            }
        }
        preferredWebMediaSource.value = "web2"

        val job = launchAutoSelect(session)
        sources.web2.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertEquals(0L, testScope().currentTime, "容忍窗 fallback 不应参与, 选择必须发生在 T0")
        assertSelectedSource(sources.web2)
        job.assertCompleted()
    }

    @Test
    fun `MIG-DUAL-01 JSON偏好web1时Room偏好web2完成有结果clause1仍落空最终选JSON侧`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any.copy(mediaSourceId = "web1")
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings()

        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 0 }
                val web2 by web { tier = 2 }
            }
        }
        preferredWebMediaSource.value = "web2"

        val job = launchAutoSelect(session)
        sources.web2.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)

        sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertSelectedSource(sources.web1)
        job.assertCompleted()
    }

    @Test
    fun `MIG-DUAL-02 JSON偏好web1无结果时仍可在精确匹配阶段选择web2`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any.copy(mediaSourceId = "web1")
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings()

        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 0 }
                val web2 by web { tier = 2 }
            }
        }
        preferredWebMediaSource.value = "web2"

        val job = launchAutoSelect(session)
        sources.web2.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)

        sources.web1.complete(emptyList<Media>())
        testScope().runCurrent()

        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)
        testScope().advanceTimeBy(5.seconds)
        testScope().runCurrent()
        assertSelectedSource(sources.web2)
        job.assertCompleted()
    }

    @Test
    fun `MIG-DUAL-03 JSON字幕组偏好与Room偏好源候选不相容时clause1落空经容忍超时兜底选中`() = runFetchMediaSelectorTestSuite {
        initSubject()
        preferenceApi.savedUserPreference.value = MediaPreference.Any.copy(alliance = "桜都字幕组")
        preferenceApi.mediaSelectorSettings.value = autoSelectSettings(lowTierToleranceDuration = 1.seconds)

        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 0 }
                val web2 by web { tier = 2 }
            }
        }
        preferredWebMediaSource.value = "web2"

        val job = launchAutoSelect(session)
        sources.web2.complete(
            media(kind = WEB, alliance = "字幕组", subjectName = initApi.subjectName),
        )
        testScope().runCurrent()

        assertNull(selector.selected.value)
        assertFalse(job.isCompleted)

        testScope().advanceTimeBy(1.seconds)
        testScope().runCurrent()

        assertSelectedSource(sources.web2)
        job.assertCompleted()
    }

    context(scope: TestScope)
    private fun FetchMediaSelectorTestSuite.launchAutoSelect(session: MediaFetchSession): Job {
        val useCase = MediaSelectorAutoSelectUseCaseImpl(createKoin())
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            useCase(session, selector)
        }
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

    private fun FetchMediaSelectorTestSuite.assertSelectedSource(source: Handle) {
        assertEquals(source.instance.mediaSourceId, selector.selected.value?.mediaSourceId)
    }

    private fun Job.assertCompleted() {
        assertTrue(isCompleted, "Auto select job should have completed")
        assertFalse(isCancelled, "Auto select job should have completed normally, but it was cancelled")
    }

    private fun autoSelectSettings(
        preferKind: MediaSourceKind? = WEB,
        fastSelectWebKind: Boolean = true,
        lowTierToleranceDuration: Duration = 5.seconds,
    ): MediaSelectorSettings = MediaSelectorSettings.AllVisible.copy(
        autoEnableLastSelected = false,
        fastSelectWebKind = fastSelectWebKind,
        preferKind = preferKind,
        fastSelectWebLowTierToleranceDuration = lowTierToleranceDuration,
    )

    private fun MediaSelectorTestSuite.initSubject() {
        initSubject("test")
    }

    context(scope: TestScope)
    private fun testScope(): TestScope = scope
}
