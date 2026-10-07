package com.wynime.app.domain.media.selector

import com.wynime.app.domain.media.selector.testFramework.assertMedias
import com.wynime.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.test.DisabledOnNative
import com.wynime.test.TestContainer
import kotlin.test.Test

@TestContainer
@DisabledOnNative
class MediaSelectorSpTest {
    @Test
    fun `when watching SP23 - match SP23`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("A") {
                episodeSort = EpisodeSort(23, EpisodeType.SP)
                episodeEp = EpisodeSort(0)
            }
            mediaApi.addSimpleWebMedia(
                subjectName = "A",
                episodeSort = EpisodeSort(23, EpisodeType.SP),
            )
        },
    ) {
        assertMedias {
            single().assert(included = true)
        }
    }

    @Test
    fun `when watching SP23 - match main 23`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("玉子市场") {
                episodeSort = EpisodeSort(23, EpisodeType.SP)
                episodeEp = EpisodeSort(0)
            }
            mediaApi.addSimpleWebMedia(
                subjectName = "玉子市场",
                episodeSort = EpisodeSort(23, EpisodeType.MainStory),
            )
        },
    ) {
        assertMedias {
            single().assert(included = true)
        }
    }

    @Test
    fun `when watching SP23 - match both SP23 and main 23`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("玉子市场") {
                episodeSort = EpisodeSort(23, EpisodeType.SP)
                episodeEp = EpisodeSort(0)
            }
            mediaApi.addSimpleWebMedia(
                subjectName = "玉子市场",
                episodeSort = EpisodeSort(23, EpisodeType.SP),
            )
            mediaApi.addSimpleWebMedia(
                subjectName = "玉子市场",
                episodeSort = EpisodeSort(23, EpisodeType.MainStory),
            )
        },
    ) {
        assertMedias {
            onSingle(
                episodeRange = EpisodeRange.Companion.single(
                    EpisodeSort(
                        23,
                        EpisodeType.SP,
                    ),
                ),
            ).assert(included = true)
            onSingle(episodeRange = EpisodeRange.Companion.single(EpisodeSort(23, EpisodeType.MainStory))).assert(
                included = true,
            )
        }
    }
}