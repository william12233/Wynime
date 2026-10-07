package com.wynime.app.domain.media.selector

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.io.files.Path
import com.wynime.app.domain.media.DroppedFileMedia
import com.wynime.app.domain.media.selector.testFramework.collectEvents
import com.wynime.app.domain.media.selector.testFramework.runFetchMediaSelectorTestSuite
import com.wynime.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import com.wynime.app.domain.media.selector.testFramework.tier
import com.wynime.app.domain.player.extension.PlayerLoadErrorHandler
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceKind.WEB
import com.wynime.test.DisabledOnNative
import com.wynime.utils.io.inSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@DisabledOnNative
class PlayerLoadErrorHandlerTest {
    @Test
    fun `ERR-05 preferKind WEB 播放失败换源到其他 WEB media 并拉黑当前`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession {
            object {
                val webA by web { tier = 0 }
                val webB by web { tier = 0 }
            }
        }
        val mediaA = media(kind = WEB, subjectName = initApi.subjectName)
        val mediaB = media(kind = WEB, subjectName = initApi.subjectName)
        sources.webA.complete(mediaA)
        sources.webB.complete(mediaB)
        testScope().runCurrent()

        selector.select(selector.filteredCandidatesMedia.first().single { it.mediaId == mediaA.mediaId })

        val handler = PlayerLoadErrorHandler(
            getPreferKind = { MediaSourceKind.WEB },
            getSourceTiers = { preferenceApi.sourceTiers!! },
        )
        val job = testScope().launch { handler.handleError(session, selector) }
        testScope().runCurrent()

        assertEquals(setOf(mediaA.mediaId), handler.blacklist)
        assertEquals(mediaA.mediaId, selector.selected.value?.mediaId)

        testScope().advanceTimeBy(999.milliseconds)
        testScope().runCurrent()
        assertFalse(job.isCompleted)
        assertEquals(mediaA.mediaId, selector.selected.value?.mediaId)

        testScope().advanceTimeBy(2.milliseconds)
        testScope().runCurrent()

        assertTrue(job.isCompleted)
        assertEquals(mediaB.mediaId, selector.selected.value?.mediaId)
        assertEquals(setOf(mediaA.mediaId), handler.blacklist)
    }

    @Test
    fun `拖入的本地文件播放失败不换源也不拉黑`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession {
            object {
                val webA by web { tier = 0 }
            }
        }

        sources.webA.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()

        val dropped = DroppedFileMedia.create(Path("/videos/episode-01.mkv").inSystem)
        selector.selectTemporarily(dropped)

        val handler = PlayerLoadErrorHandler(
            getPreferKind = { MediaSourceKind.WEB },
            getSourceTiers = { preferenceApi.sourceTiers!! },
        )
        val collected = selector.collectEvents {
            val job = testScope().launch { handler.handleError(session, selector) }
            testScope().advanceTimeBy(10.seconds)
            testScope().runCurrent()
            assertTrue(job.isCompleted)
        }

        collected.expectNoEvents()
        assertEquals(dropped, selector.selected.value)
        assertTrue(handler.blacklist.isEmpty())
    }

    @Test
    fun `ERR-05 高 tier 源只能由 1s 容忍窗超时 fallback 选中`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession {
            object {

                val webA by web { tier = 1 }
            }
        }
        val mediaA = media(kind = WEB, subjectName = initApi.subjectName)
        val mediaB = media(kind = WEB, subjectName = initApi.subjectName)
        sources.webA.complete(mediaA, mediaB)
        testScope().runCurrent()

        selector.select(selector.filteredCandidatesMedia.first().single { it.mediaId == mediaA.mediaId })

        val handler = PlayerLoadErrorHandler(
            getPreferKind = { MediaSourceKind.WEB },
            getSourceTiers = { preferenceApi.sourceTiers!! },
        )
        val job = testScope().launch { handler.handleError(session, selector) }
        testScope().runCurrent()

        assertEquals(setOf(mediaA.mediaId), handler.blacklist)
        assertEquals(mediaA.mediaId, selector.selected.value?.mediaId)

        testScope().advanceTimeBy(1.seconds + 999.milliseconds)
        testScope().runCurrent()
        assertFalse(job.isCompleted)
        assertEquals(mediaA.mediaId, selector.selected.value?.mediaId)

        testScope().advanceTimeBy(2.milliseconds)
        testScope().runCurrent()

        assertTrue(job.isCompleted)
        assertEquals(mediaB.mediaId, selector.selected.value?.mediaId)
        assertEquals(setOf(mediaA.mediaId), handler.blacklist)
    }

    @Test
    fun `ERR-05 non-WEB preferKind 不自动換源`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession {
            object {
                val webA by web { tier = 0 }
                val webB by web { tier = 0 }
            }
        }
        val mediaA = media(kind = WEB, subjectName = initApi.subjectName)
        val mediaB = media(kind = WEB, subjectName = initApi.subjectName)
        sources.webA.complete(mediaA)
        sources.webB.complete(mediaB)
        testScope().runCurrent()

        selector.select(selector.filteredCandidatesMedia.first().single { it.mediaId == mediaA.mediaId })

        val handler = PlayerLoadErrorHandler(
            getPreferKind = { null },
            getSourceTiers = { preferenceApi.sourceTiers!! },
        )
        val job = testScope().launch { handler.handleError(session, selector) }
        testScope().advanceUntilIdle()

        assertTrue(job.isCompleted)
        assertEquals(mediaA.mediaId, selector.selected.value?.mediaId)
        assertEquals(setOf(mediaA.mediaId), handler.blacklist)
    }

    @Test
    fun `ERR-05 候选全在黑名单时保持当前选择`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession {
            object {
                val webA by web { tier = 0 }
                val webB by web { tier = 0 }
            }
        }
        val mediaA = media(kind = WEB, subjectName = initApi.subjectName)
        val mediaB = media(kind = WEB, subjectName = initApi.subjectName)
        sources.webA.complete(mediaA)
        sources.webB.complete(mediaB)
        testScope().runCurrent()

        selector.select(selector.filteredCandidatesMedia.first().single { it.mediaId == mediaA.mediaId })

        val handler = PlayerLoadErrorHandler(
            getPreferKind = { MediaSourceKind.WEB },
            getSourceTiers = { preferenceApi.sourceTiers!! },
        )
        testScope().launch { handler.handleError(session, selector) }
        testScope().advanceUntilIdle()
        assertEquals(mediaB.mediaId, selector.selected.value?.mediaId)

        lateinit var job: Job
        val collected = selector.collectEvents {
            job = testScope().launch { handler.handleError(session, selector) }
            testScope().advanceUntilIdle()
        }

        assertEquals(0, collected.onSelect.size)
        collected.expectNoEvents()
        assertTrue(job.isCompleted)
        assertEquals(mediaB.mediaId, selector.selected.value?.mediaId)
        assertEquals(setOf(mediaA.mediaId, mediaB.mediaId), handler.blacklist)
    }

    @Test
    fun `ERR-04 trySelectDefault 的 onSelect previousMedia 为 null 不进黑名单`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            mediaApi.addMedia(media(alliance = "组A"))
            mediaApi.addMedia(media(alliance = "组B"))
        },
    ) {
        val handler = PlayerLoadErrorHandler(
            getPreferKind = { null },
            getSourceTiers = { MediaSelectorSourceTiers(emptyMap()) },
        )

        coroutineScope {
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                handler.observeMediaSelectorBlacklist(
                    mediaSelectorFlow = flowOf(selector),
                )
            }
            testScope.runCurrent()

            val defaultSelected = assertNotNull(selector.trySelectDefault())
            testScope.advanceUntilIdle()

            assertTrue(handler.blacklist.isEmpty())

            selector.select(mediaApi.mediaList.value.first { it.mediaId != defaultSelected.mediaId })
            testScope.advanceUntilIdle()
            assertEquals(setOf(defaultSelected.mediaId), handler.blacklist)

            job.cancel()
        }
    }

    @Test
    fun `ERR-03 黑名单不跨 handler 实例共享`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession {
            object {
                val webA by web { tier = 0 }
                val webB by web { tier = 0 }
            }
        }
        val mediaA = media(kind = WEB, subjectName = initApi.subjectName)
        val mediaB = media(kind = WEB, subjectName = initApi.subjectName)
        sources.webA.complete(mediaA)
        sources.webB.complete(mediaB)
        testScope().runCurrent()

        selector.select(selector.filteredCandidatesMedia.first().single { it.mediaId == mediaA.mediaId })

        val handler1 = PlayerLoadErrorHandler(
            getPreferKind = { MediaSourceKind.WEB },
            getSourceTiers = { preferenceApi.sourceTiers!! },
        )
        testScope().launch { handler1.handleError(session, selector) }
        testScope().advanceUntilIdle()
        assertEquals(mediaB.mediaId, selector.selected.value?.mediaId)

        val handler2 = PlayerLoadErrorHandler(
            getPreferKind = { MediaSourceKind.WEB },
            getSourceTiers = { preferenceApi.sourceTiers!! },
        )
        assertTrue(handler2.blacklist.isEmpty())
        val job = testScope().launch { handler2.handleError(session, selector) }
        testScope().advanceUntilIdle()

        assertTrue(job.isCompleted)
        assertEquals(mediaA.mediaId, selector.selected.value?.mediaId)
        assertEquals(setOf(mediaB.mediaId), handler2.blacklist)
        assertEquals(setOf(mediaA.mediaId), handler1.blacklist)
    }

    @Test
    fun `manual choice during player error delay cancels automatic replacement`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession { object { val web1 by web { tier = 0 } } }
        sources.web1.complete(media(kind = WEB, subjectName = initApi.subjectName))
        testScope().runCurrent()
        val failed = media(kind = WEB, subjectName = initApi.subjectName)
        selector.select(failed)
        val handler = PlayerLoadErrorHandler(getPreferKind = { WEB }, getSourceTiers = { preferenceApi.sourceTiers!! })
        val job = testScope().launch { handler.handleError(session, selector) }
        testScope().runCurrent()
        val manual = media(kind = WEB, subjectName = initApi.subjectName)
        selector.select(manual)
        testScope().advanceUntilIdle()
        assertTrue(job.isCompleted)
        assertEquals(manual, selector.selected.value)
    }

    @Test
    fun `player error skips ready and pending local caches`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession {
            object {
                val cached by localCache()
                val pendingCache by localCache()
                val web1 by web { tier = 0 }
            }
        }
        val failed = media(kind = WEB, subjectName = initApi.subjectName)
        val replacement = media(kind = WEB, subjectName = initApi.subjectName)
        sources.web1.complete(failed, replacement)
        sources.cached.complete(media(kind = MediaSourceKind.LocalCache, subjectName = initApi.subjectName))
        testScope().runCurrent()
        selector.select(selector.filteredCandidatesMedia.first().single { it.mediaId == failed.mediaId })

        val handler = PlayerLoadErrorHandler(getPreferKind = { WEB }, getSourceTiers = { preferenceApi.sourceTiers!! })
        val job = testScope().launch { handler.handleError(session, selector) }
        testScope().advanceUntilIdle()

        assertTrue(job.isCompleted)
        assertEquals(replacement.mediaId, selector.selected.value?.mediaId)
        assertEquals(setOf(failed.mediaId), handler.blacklist)
    }

    context(scope: TestScope)
    private fun testScope(): TestScope = scope
}
