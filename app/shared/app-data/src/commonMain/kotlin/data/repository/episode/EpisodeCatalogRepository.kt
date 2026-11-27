package com.wynime.app.data.repository.episode

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.repository.RepositoryNetworkException
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.datasources.api.paging.Paged
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@Serializable
data class CachedEpisodeCatalog(
    val episodes: List<EpisodeInfo>,
    val updatedAtMillis: Long,
)

class EpisodeCatalogRepository(
    private val store: DataStore<Map<Int, CachedEpisodeCatalog>>,
    private val loadPage: suspend (subjectId: Int, offset: Int) -> Paged<EpisodeInfo>,
    private val clock: Clock = Clock.System,
) {
    private val refreshMutex = Mutex()

    fun catalogFlow(subjectId: Int) = store.data.map { it[subjectId] }.distinctUntilChanged()

    suspend fun refresh(subjectId: Int, force: Boolean = false): Unit = refreshMutex.withLock {
        val now = clock.now().toEpochMilliseconds()
        val cached = catalogFlow(subjectId).first()
        if (!force && cached != null && now - cached.updatedAtMillis < 1.hours.inWholeMilliseconds) {
            return@withLock
        }

        val episodes = withTimeoutOrNull(15.seconds) { loadCatalog(subjectId) }
            ?: throw RepositoryNetworkException("Episode list timed out")
        store.updateData { it + (subjectId to CachedEpisodeCatalog(episodes, now)) }
    }

    private suspend fun loadCatalog(subjectId: Int): List<EpisodeInfo> {
        val episodes = mutableMapOf<Int, EpisodeInfo>()
        var offset = 0
        var total: Int? = null
        while (true) {
            val page = loadPage(subjectId, offset)
            if ((total != null && page.total != null && total != page.total) ||
                (page.hasMore && page.page.isEmpty())
            ) {
                throw RepositoryRequestError("Bangumi 集數分頁不完整")
            }
            total = page.total ?: total
            for (episode in page.page) {
                if (episode.episodeId <= 0 || episodes.put(episode.episodeId, episode) != null) {
                    throw RepositoryRequestError("Bangumi 集數資料有無效或重複的集數")
                }
            }
            if (!page.hasMore) {
                if (total != null && episodes.size != total) {
                    throw RepositoryRequestError("Bangumi 集數分頁不完整")
                }
                return episodes.values.sortedBy { it.sort }
            }
            offset += page.page.size
        }
    }
}
