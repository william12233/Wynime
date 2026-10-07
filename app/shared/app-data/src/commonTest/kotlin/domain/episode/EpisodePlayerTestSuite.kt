package com.wynime.app.domain.episode

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.data.models.subject.SubjectSeriesInfo
import com.wynime.app.data.models.subject.TestSubjectCollections
import com.wynime.app.domain.media.hls.HlsPlaybackPreparer
import com.wynime.app.domain.media.hls.NoopHlsPlaybackPreparer
import com.wynime.app.domain.settings.GetVideoScaffoldConfigUseCase
import org.koin.core.Koin
import org.koin.dsl.module
import org.openani.mediamp.test.TestMediampPlayer

class EpisodePlayerTestSuite(
    testScope: TestScope,
    val backgroundScope: CoroutineScope = testScope.backgroundScope,
) {
    val player = TestMediampPlayer(StandardTestDispatcher(testScope.testScheduler))

    @Suppress("DEPRECATION")
    val mediaSelectorTestBuilder = com.wynime.app.domain.media.selector.legacy.MediaSelectorTestBuilder(testScope)

    val koin = Koin()

    init {
        koin.loadModules(
            listOf(
                module {
                    single<GetSubjectEpisodeInfoBundleFlowUseCase> {
                        GetSubjectEpisodeInfoBundleFlowUseCase { idsFlow ->
                            idsFlow.map {
                                SubjectEpisodeInfoBundle(
                                    it.subjectId,
                                    it.episodeId,
                                    TestSubjectCollections[0].run {
                                        copy(subjectInfo = subjectInfo.copy(subjectId = it.subjectId))
                                    },
                                    TestSubjectCollections[0].episodes[0].run {
                                        copy(episodeInfo = episodeInfo.copy(episodeId = it.episodeId))
                                    },
                                    seriesInfo = SubjectSeriesInfo.Fallback,
                                    subjectCompleted = false,
                                )
                            }
                        }
                    }
                    single<CreateMediaFetchSelectBundleFlowUseCase> {
                        CreateMediaFetchSelectBundleFlowUseCase { _ ->
                            val mediaFetchSession =
                                mediaSelectorTestBuilder.createMediaFetchSession(mediaSelectorTestBuilder.createMediaFetcher())
                            flowOf(
                                MediaFetchSelectBundle(
                                    mediaFetchSession,
                                    mediaSelectorTestBuilder.createMediaSelector(mediaFetchSession),
                                ),
                            )
                        }
                    }
                    single<GetVideoScaffoldConfigUseCase> {
                        GetVideoScaffoldConfigUseCase {
                            flowOf(VideoScaffoldConfig.AllDisabled)
                        }
                    }
                    single<HlsPlaybackPreparer> {
                        NoopHlsPlaybackPreparer
                    }
                },
            ),
        )
    }

    class ComponentScope(
        val koin: Koin,
    )

    inline fun <reified T : Any> registerComponent(crossinline value: ComponentScope.() -> T) {
        koin.loadModules(
            listOf(
                module {
                    single<T> { value(ComponentScope(getKoin())) }
                },
            ),
        )
    }
}

fun TestScope.createExceptionCapturingSupervisorScope(parentScope: CoroutineScope = backgroundScope): Pair<CoroutineScope, CompletableDeferred<Throwable>> {
    val backgroundException = CompletableDeferred<Throwable>()
    val scope = CoroutineScope(
        (parentScope.coroutineContext.minusKey(Job)) + SupervisorJob(parentScope.coroutineContext[Job]) + CoroutineExceptionHandler { _, throwable ->
            backgroundException.complete(throwable)
        },
    )
    return Pair(scope, backgroundException)
}
