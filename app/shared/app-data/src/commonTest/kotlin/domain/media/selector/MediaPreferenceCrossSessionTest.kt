package com.wynime.app.domain.media.selector

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.persistent.createTestPreferencesDataStore
import com.wynime.app.data.persistent.database.dao.createMemoryPreferredWebMediaSourceDao
import com.wynime.app.data.repository.media.EpisodePreferencesRepository
import com.wynime.app.data.repository.media.EpisodePreferencesRepositoryImpl
import com.wynime.app.domain.media.selector.testFramework.MediaSelectorTestSuite
import com.wynime.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.SOURCE_PRIMARY_WEB
import com.wynime.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.SOURCE_SECONDARY_WEB
import com.wynime.app.domain.media.selector.testFramework.SimpleMediaSelectorTestSuite
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.test.TestContainer
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestContainer
class MediaPreferenceCrossSessionTest {
    private val globalDefault = MutableStateFlow(MediaSelectorTestSuite.DEFAULT_PREFERENCE)

    private fun createRepository(store: DataStore<Preferences>): EpisodePreferencesRepositoryImpl {
        return EpisodePreferencesRepositoryImpl(
            store = store,
            preferredWebMediaSourceDao = createMemoryPreferredWebMediaSourceDao(),
            defaultMediaPreference = globalDefault,
        )
    }

    private fun TestScope.createSessionSelector(
        suite: SimpleMediaSelectorTestSuite,
        repository: EpisodePreferencesRepository,
        mediaList: Flow<List<Media>>,
        enableCaching: Boolean = false,
        cachingScope: CoroutineScope? = null,
    ): DefaultMediaSelector = DefaultMediaSelector(
        mediaSelectorContextNotCached = suite.preferenceApi.mediaSelectorContext,
        mediaListNotCached = mediaList,
        savedUserPreference = repository.mediaPreferenceFlow(SUBJECT_ID),
        savedDefaultPreference = globalDefault,
        mediaSelectorSettings = suite.preferenceApi.mediaSelectorSettings,
        flowCoroutineContext = coroutineContext[ContinuationInterceptor]!!,
        enableCaching = enableCaching,
        cachingScope = cachingScope,
    )

    private suspend fun TestScope.runSessionA(
        suite: SimpleMediaSelectorTestSuite,
        repository: EpisodePreferencesRepository,
        target: Media,
        mediaList: List<Media>,
    ) {
        val selectorA = createSessionSelector(suite, repository, MutableStateFlow(mediaList))
        val mountA: Job = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            selectorA.eventHandling.savePreferenceOnSelect { repository.setMediaPreference(SUBJECT_ID, it) }
        }
        selectorA.events.onChangePreference.subscriptionCount.first { it > 0 }

        assertTrue(selectorA.select(target))
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()

        mountA.cancel()
    }

    private fun createTargetMedia(suite: SimpleMediaSelectorTestSuite) = suite.media(
        sourceId = SOURCE_SECONDARY_WEB,
        alliance = "桜都字幕组",
        resolution = "720P",
        subtitleLanguages = listOf("CHT"),
        kind = MediaSourceKind.WEB,
    )

    private fun createCompetitorMedia(suite: SimpleMediaSelectorTestSuite) = suite.media(
        sourceId = SOURCE_PRIMARY_WEB,
        alliance = "北宇治字幕组",
        resolution = "1080P",
        subtitleLanguages = listOf("CHS", "CHT"),
        kind = MediaSourceKind.WEB,
    )

    @Test
    fun `A11 会话A手动选择debounce落库 会话B读回存档过滤偏好并选回同源同字幕组`() = runTest {
        val store = createTestPreferencesDataStore()
        val suite = SimpleMediaSelectorTestSuite(this)
        suite.initSubject("孤独摇滚")
        val target = createTargetMedia(suite)
        val competitor = createCompetitorMedia(suite)

        val repositoryA = createRepository(store)
        val selectorA = createSessionSelector(suite, repositoryA, MutableStateFlow(listOf(competitor, target)))
        val mountA = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            selectorA.eventHandling.savePreferenceOnSelect { repositoryA.setMediaPreference(SUBJECT_ID, it) }
        }
        selectorA.events.onChangePreference.subscriptionCount.first { it > 0 }

        assertTrue(selectorA.select(target))
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()

        val savedPreference = repositoryA.mediaPreferenceFlow(SUBJECT_ID).first()
        assertEquals(
            MediaSelectorTestSuite.DEFAULT_PREFERENCE.copy(
                alliance = "桜都字幕组",
                resolution = "720P",
                subtitleLanguageId = "CHT",
                mediaSourceId = SOURCE_SECONDARY_WEB,
            ),
            savedPreference,
        )

        mountA.cancel()

        val repositoryB = createRepository(store)
        val selectorB = createSessionSelector(suite, repositoryB, MutableStateFlow(listOf(competitor, target)))

        assertEquals(setOf(competitor, target), selectorB.filteredCandidatesMedia.first().toSet())
        assertEquals(listOf(target), selectorB.preferredCandidatesMedia.first())

        assertEquals(SOURCE_SECONDARY_WEB, selectorB.mediaSourceId.finalSelected.first())
        assertEquals("桜都字幕组", selectorB.alliance.finalSelected.first())
        assertEquals("720P", selectorB.resolution.finalSelected.first())
        assertEquals("CHT", selectorB.subtitleLanguageId.finalSelected.first())
        assertEquals(target, selectorB.trySelectDefault())
        assertEquals(target, selectorB.selected.value)
    }

    @Test
    fun `A11 会话B在生产默认 enableCaching 为 true 下同样读回存档并按四字段过滤`() = runTest {
        val store = createTestPreferencesDataStore()
        val suite = SimpleMediaSelectorTestSuite(this)
        suite.initSubject("孤独摇滚")
        val target = createTargetMedia(suite)
        val competitor = createCompetitorMedia(suite)

        val repositoryA = createRepository(store)
        runSessionA(suite, repositoryA, target, listOf(competitor, target))

        val repositoryB = createRepository(store)
        val selectorB = createSessionSelector(
            suite, repositoryB, MutableStateFlow(listOf(competitor, target)),
            enableCaching = true,
            cachingScope = backgroundScope,
        )
        advanceUntilIdle()

        assertEquals(setOf(competitor, target), selectorB.filteredCandidatesMedia.first().toSet())
        assertEquals(listOf(target), selectorB.preferredCandidatesMedia.first())
        assertEquals(SOURCE_SECONDARY_WEB, selectorB.mediaSourceId.finalSelected.first())
        assertEquals("桜都字幕组", selectorB.alliance.finalSelected.first())
        assertEquals("720P", selectorB.resolution.finalSelected.first())
        assertEquals("CHT", selectorB.subtitleLanguageId.finalSelected.first())
        assertEquals(target, selectorB.trySelectDefault())
        assertEquals(target, selectorB.selected.value)
    }

    @Test
    fun `A11 对照 空存档时会话B不按偏好过滤且 mediaSourceId finalSelected 为空`() = runTest {
        val store = createTestPreferencesDataStore()
        val suite = SimpleMediaSelectorTestSuite(this)
        suite.initSubject("孤独摇滚")
        val target = createTargetMedia(suite)
        val competitor = createCompetitorMedia(suite)

        val repository = createRepository(store)
        val selector = createSessionSelector(suite, repository, MutableStateFlow(listOf(competitor, target)))

        assertEquals(setOf(competitor, target), selector.preferredCandidatesMedia.first().toSet())
        assertNull(selector.mediaSourceId.finalSelected.first())
        assertNull(selector.alliance.finalSelected.first())
    }

    private companion object {
        private const val SUBJECT_ID = 100
    }
}
