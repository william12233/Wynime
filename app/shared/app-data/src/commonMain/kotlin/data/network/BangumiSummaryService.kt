package com.wynime.app.data.network

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.wynime.app.domain.foundation.HttpClientProvider
import com.wynime.app.domain.foundation.get
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import kotlin.coroutines.CoroutineContext

class BangumiSummaryService(
    httpClientProvider: HttpClientProvider,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    private val client = httpClientProvider.get()
    private val json = Json { ignoreUnknownKeys = true }

    private val cacheLock = Mutex()
    private val cache = mutableMapOf<Int, String>()

    suspend fun getSummary(subjectId: Int): String? = withContext(ioDispatcher) {
        cacheLock.withLock { cache[subjectId] }?.let { return@withContext it.ifEmpty { null } }

        val summary = try {
            val body = client.use { get("$BGM_API_BASE_URL/v0/subjects/$subjectId").bodyAsText() }
            json.decodeFromString(BangumiSubjectSummary.serializer(), body).summary.trim()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClientRequestException) {

            logger.info { "bgm.tv subject $subjectId not accessible (${e.response.status}), treat as no summary" }
            ""
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch bgm.tv summary for subject $subjectId, will retry next time" }
            throw e
        }

        logger.info { "bgm.tv summary for subject $subjectId: ${if (summary.isEmpty()) "not found" else "${summary.length} chars"}" }
        cacheLock.withLock { cache[subjectId] = summary }
        summary.ifEmpty { null }
    }

    @Serializable
    private data class BangumiSubjectSummary(
        val summary: String = "",
    )

    private companion object {
        private const val BGM_API_BASE_URL = "https://api.bgm.tv"
        private val logger = logger<BangumiSummaryService>()
    }
}
