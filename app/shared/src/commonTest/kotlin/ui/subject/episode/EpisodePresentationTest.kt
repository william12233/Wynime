package com.wynime.app.ui.subject.episode

import com.wynime.app.data.models.episode.EpisodeCollectionInfo
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodePresentationTest {
    private fun episode(name: String, nameCn: String) = EpisodeCollectionInfo(
        episodeInfo = EpisodeInfo(episodeId = 1, type = EpisodeType.MainStory, name = name, nameCn = nameCn),
        collectionType = UnifiedCollectionType.WISH,
    )

    @Test
    fun `toPresentation_sets_title_and_originalTitle_from_displayName_and_name`() {
        val presentation = episode("Bocchi the Rock!", "孤独摇滚！").toPresentation(recurrence = null)
        assertEquals("孤独摇滚！", presentation.title)
        assertEquals("Bocchi the Rock!", presentation.originalTitle)
    }

    @Test
    fun `originalTitle_falls_back_to_title_when_name_is_blank`() {
        val presentation = episode("", "孤独摇滚！").toPresentation(recurrence = null)
        assertEquals("孤独摇滚！", presentation.title)
        assertEquals("孤独摇滚！", presentation.originalTitle)
    }
}
