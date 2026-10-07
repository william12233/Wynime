package com.wynime.app.data.models

import com.wynime.app.domain.episode.EpisodeCollections
import com.wynime.datasources.api.PackedDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpisodeCollectionsTest {

    fun `subject completed after 365 days`() {
        assertTrue {
            EpisodeCollections.isSubjectCompleted(
                sequenceOf(
                    PackedDate(2023, 10, 4),
                    PackedDate(2023, 10, 11),
                ),
                now = PackedDate(2024, 10, 11),
            )
        }
    }

    @Test
    fun `subject not completed within 14 days`() {
        assertFalse {
            EpisodeCollections.isSubjectCompleted(
                sequenceOf(
                    PackedDate(2023, 10, 4),
                    PackedDate(2023, 10, 11),
                ),
                now = PackedDate(2024, 10, 9),
            )
        }
    }

    @Test
    fun `subject not completed before first start`() {
        assertFalse {
            EpisodeCollections.isSubjectCompleted(
                sequenceOf(
                    PackedDate(2023, 10, 4),
                    PackedDate(2023, 10, 11),
                ),
                now = PackedDate(2022, 10, 11),
            )
        }
    }

    @Test
    fun `subject not completed before last start`() {
        assertFalse {
            EpisodeCollections.isSubjectCompleted(
                sequenceOf(
                    PackedDate(2023, 10, 4),
                    PackedDate(2023, 10, 11),
                ),
                now = PackedDate(2022, 10, 10),
            )
        }
    }
}