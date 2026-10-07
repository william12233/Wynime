package com.wynime.app.data.persistent.database.dao

import androidx.sqlite.SQLiteException
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.wynime.app.data.models.subject.RatingInfo
import com.wynime.app.data.models.subject.SelfRatingInfo
import com.wynime.app.data.models.subject.SubjectCollectionStats
import com.wynime.app.data.persistent.database.WynimeDatabase
import com.wynime.app.data.persistent.database.createTestWynimeDatabase
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PreferredWebMediaSourceDaoTest {
    private fun runDatabaseTest(block: suspend (WynimeDatabase) -> Unit) = runBlocking {
        val database = createTestWynimeDatabase()
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun subjectCollection(subjectId: Int) = SubjectCollectionEntity(
        subjectId = subjectId,
        name = "test",
        nameCn = "测试",
        summary = "",
        nsfw = false,
        imageLarge = "",
        totalEpisodes = 12,
        airDate = PackedDate.Invalid,
        aliases = emptyList(),
        tags = emptyList(),
        collectionStats = SubjectCollectionStats.Zero,
        ratingInfo = RatingInfo.Empty,
        completeDate = PackedDate.Invalid,
        selfRatingInfo = SelfRatingInfo.Empty,
        collectionType = UnifiedCollectionType.DOING,
        recurrence = null,
        lastUpdated = 0,
        lastFetched = 0,
        cachedStaffUpdated = 0,
        cachedCharactersUpdated = 0,
    )

    @Test
    fun `ROOM-03 upsert 后 flow 读回, 再次 upsert 覆盖`() = runDatabaseTest { database ->
        database.subjectCollection().upsert(subjectCollection(1))
        val dao = database.preferredWebMediaSourceDao()

        dao.setPreferredMediaSource(PreferredWebMediaSource(subjectId = 1, mediaSourceId = "source-a"))
        assertEquals("source-a", dao.getPreferredMediaSourceId(1).first())

        dao.setPreferredMediaSource(PreferredWebMediaSource(subjectId = 1, mediaSourceId = "source-b"))
        assertEquals("source-b", dao.getPreferredMediaSourceId(1).first())

        assertNull(dao.getPreferredMediaSourceId(2).first())
    }

    @Test
    fun `ROOM-03 subjectId 不在收藏表时 upsert 抛 FK 异常`() = runDatabaseTest { database ->
        val dao = database.preferredWebMediaSourceDao()

        val exception = assertFailsWith<SQLiteException> {
            dao.setPreferredMediaSource(PreferredWebMediaSource(subjectId = 42, mediaSourceId = "source-a"))
        }
        assertContains(exception.message.orEmpty(), "FOREIGN KEY")
        assertNull(dao.getPreferredMediaSourceId(42).first())
    }

    @Test
    fun `ROOM-03 删除收藏级联删除偏好且 flow emit null`() = runDatabaseTest { database ->
        database.subjectCollection().upsert(subjectCollection(1))
        val dao = database.preferredWebMediaSourceDao()
        dao.setPreferredMediaSource(PreferredWebMediaSource(subjectId = 1, mediaSourceId = "source-a"))

        dao.getPreferredMediaSourceId(1).test {
            assertEquals("source-a", awaitItem())

            database.subjectCollection().delete(1)

            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(dao.getPreferredMediaSourceId(1).first())
    }
}
