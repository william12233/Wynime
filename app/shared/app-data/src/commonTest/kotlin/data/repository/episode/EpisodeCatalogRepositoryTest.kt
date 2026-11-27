package com.wynime.app.data.repository.episode

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.persistent.MemoryDataStore
import com.wynime.app.data.repository.RepositoryNetworkException
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.paging.Paged
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EpisodeCatalogRepositoryTest {
    @Test
    fun completeCatalogSurvivesReopeningAndARefreshTimeout() = runTest {
        val store = MemoryDataStore<Map<Int, CachedEpisodeCatalog>>(emptyMap())
        val first = EpisodeInfo(1, EpisodeType.MainStory, sort = EpisodeSort(1))
        val second = EpisodeInfo(2, EpisodeType.MainStory, sort = EpisodeSort(2))
        val offsets = mutableListOf<Int>()
        val repository = EpisodeCatalogRepository(store, { _, offset ->
            offsets += offset
            if (offset == 0) Paged(2, true, listOf(first)) else Paged(2, false, listOf(second))
        })
        repository.refresh(10)
        assertEquals(listOf(0, 1), offsets)

        val offlineRepository = EpisodeCatalogRepository(store, { _, _ -> awaitCancellation() })
        assertEquals(listOf(first, second), offlineRepository.catalogFlow(10).first()?.episodes)
        assertFailsWith<RepositoryNetworkException> { offlineRepository.refresh(10, force = true) }
        assertEquals(listOf(first, second), offlineRepository.catalogFlow(10).first()?.episodes)
    }

    @Test
    fun repeatedPageDoesNotReplaceTheSavedCatalog() = runTest {
        val episode = EpisodeInfo(1, EpisodeType.MainStory)
        val saved = CachedEpisodeCatalog(listOf(episode), updatedAtMillis = 0)
        val store = MemoryDataStore(mapOf(10 to saved))
        val repository = EpisodeCatalogRepository(store, { _, offset ->
            Paged(2, offset == 0, listOf(episode))
        })
        assertFailsWith<RepositoryRequestError> { repository.refresh(10, force = true) }
        assertEquals(saved, repository.catalogFlow(10).first())
    }
}
