package com.wynime.app.data.repository.episode

import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.repository.subject.toEntity1
import com.wynime.models.EpisodeCollectionDto
import com.wynime.models.EpisodeTypeDto
import com.wynime.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeCollectionMappersTest {
    private fun wynimeEpisode(imageMedium: String?, imageLarge: String?) = EpisodeCollectionDto(
        episodeId = 10,
        subjectId = 1,
        sort = "1",
        type = EpisodeTypeDto.MAIN,
        name = "ep",
        nameCn = "第1集",
        description = "",
        ep = "1",
        airdate = "2026-01-01",
        imageMedium = imageMedium,
        imageLarge = imageLarge,
    )

    @Test
    fun `still urls survive network to entity to info`() {
        val entity = wynimeEpisode("m", "l").toEntity1(subjectId = 1, lastFetched = 0L)
        assertEquals("m", entity.imageMedium)
        assertEquals("l", entity.imageLarge)

        val info = entity.toEpisodeCollectionInfo().episodeInfo
        assertEquals("m", info.imageMedium)
        assertEquals("l", info.imageLarge)
    }

    @Test
    fun `still urls survive network to info directly`() {
        val info = wynimeEpisode("m", "l").toEpisodeCollectionInfo().episodeInfo
        assertEquals("m", info.imageMedium)
        assertEquals("l", info.imageLarge)
    }

    @Test
    fun `still urls survive info to entity`() {
        val info = wynimeEpisode("m", "l").toEpisodeCollectionInfo()
        val entity = EpisodeCollectionInfo(info.episodeInfo, UnifiedCollectionType.DONE).toEntity(subjectId = 1, lastFetched = 0L)
        assertEquals("m", entity.imageMedium)
        assertEquals("l", entity.imageLarge)
        assertEquals(UnifiedCollectionType.DONE, entity.selfCollectionType)
    }

    @Test
    fun `missing still urls stay null everywhere`() {
        val entity = wynimeEpisode(null, null).toEntity1(subjectId = 1, lastFetched = 0L)
        assertNull(entity.imageMedium)
        assertNull(entity.imageLarge)
        val info = entity.toEpisodeCollectionInfo().episodeInfo
        assertNull(info.imageMedium)
        assertNull(info.imageLarge)
        assertNull(wynimeEpisode(null, null).toEpisodeCollectionInfo().episodeInfo.imageMedium)
    }
}
