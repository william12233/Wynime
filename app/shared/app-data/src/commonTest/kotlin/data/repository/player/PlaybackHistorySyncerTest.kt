package com.wynime.app.data.repository.player

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.wynime.models.PlaybackHistoryDeleteDto
import com.wynime.models.PlaybackHistorySyncRequestDto
import com.wynime.models.PlaybackHistoryUpsertDto
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackHistorySyncerTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun `api request serializes playback history ops with op type discriminator`() {
        val encoded = json.encodeToString(
            PlaybackHistorySyncRequestDto(
                ops = listOf(
                    PlaybackHistoryUpsertDto(
                        episodeId = 1,
                        subjectId = 10,
                        positionMillis = 20_000,
                        durationMillis = 100_000,
                        updatedAt = "1970-01-01T00:00:00.100Z",
                    ),
                    PlaybackHistoryDeleteDto(
                        episodeId = 2,
                        deletedAt = "1970-01-01T00:00:00.200Z",
                    ),
                ),
                lastSyncAt = "1970-01-01T00:00:00Z",
            ),
        )

        val ops = json.parseToJsonElement(encoded).jsonObject.getValue("ops").jsonArray
        assertEquals("UPSERT", ops[0].jsonObject.getValue("opType").jsonPrimitive.content)
        assertEquals("DELETE", ops[1].jsonObject.getValue("opType").jsonPrimitive.content)
    }

    @Test
    fun `first request runs immediately and requests during cooldown merge into one trailing run`() = runTest {
        val runTimes = mutableListOf<Long>()
        val gate = LeadingTrailingSyncGate(backgroundScope, 5.seconds) {
            runTimes += testScheduler.currentTime
        }

        gate.request()
        runCurrent()
        assertEquals(listOf(0L), runTimes)

        gate.request()
        gate.request()
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(listOf(0L), runTimes)

        gate.request()
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(0L, 5_000L), runTimes)
    }

    @Test
    fun `request after an idle cooldown runs immediately`() = runTest {
        val runTimes = mutableListOf<Long>()
        val gate = LeadingTrailingSyncGate(backgroundScope, 5.seconds) {
            runTimes += testScheduler.currentTime
        }

        gate.request()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        gate.request()
        runCurrent()
        assertEquals(listOf(0L, 5_000L), runTimes)
    }

    @Test
    fun `pending operations keep only the latest value per episode`() {
        val latest = listOf(
            PlaybackHistoryPendingOp.Upsert(
                id = 1,
                episodeId = 10,
                subjectId = 100,
                positionMillis = 1_000,
                durationMillis = 100_000,
                updatedAtMillis = 100,
            ),
            PlaybackHistoryPendingOp.Upsert(
                id = 2,
                episodeId = 10,
                subjectId = 100,
                positionMillis = 2_000,
                durationMillis = 100_000,
                updatedAtMillis = 200,
            ),
            PlaybackHistoryPendingOp.Upsert(
                id = 3,
                episodeId = 20,
                subjectId = 200,
                positionMillis = 3_000,
                durationMillis = 100_000,
                updatedAtMillis = 300,
            ),
            PlaybackHistoryPendingOp.Delete(
                id = 4,
                episodeId = 10,
                deletedAtMillis = 200,
            ),
            PlaybackHistoryPendingOp.Delete(
                id = 5,
                episodeId = 20,
                deletedAtMillis = 200,
            ),
        ).latestPerEpisode()

        assertEquals(listOf(3L, 4L), latest.map { it.id })
    }
}
