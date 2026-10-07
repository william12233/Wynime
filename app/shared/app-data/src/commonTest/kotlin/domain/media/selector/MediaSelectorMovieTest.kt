@file:Suppress("SameParameterValue")

package com.wynime.app.domain.media.selector

import com.wynime.app.domain.media.selector.MediaExclusionReason.FromSeriesSeason
import com.wynime.app.domain.media.selector.testFramework.addSimpleMediaSelectorTest
import com.wynime.test.TestContainer
import com.wynime.test.TestFactory
import com.wynime.test.runDynamicTests

@TestContainer
class MediaSelectorMovieTest {
    @TestFactory
    fun `exclude movie when playing main subject`() = runDynamicTests {
        addSimpleMediaSelectorTest(
            "玉子市场",
            {
                initSubject("玉子市场") {
                    aliases(
                        "Tamako Market",
                        "たまこまーけっと",
                    )
                    seriesInfo(seasonSort = 1) {
                        series(

                            "玉子市场 剧场版",
                            "玉子爱情故事",
                            "Tamako Love Story",
                            "たまこラブストーリー",
                        )
                    }
                }
            },
        ) {
            checkSubjectExclusion {
                expect(
                    "玉子市场" to null,
                    "玉子市场 剧场版" to FromSeriesSeason,
                    "玉子爱情故事" to FromSeriesSeason,
                    "Tamako Market" to null,
                    "Tamako Love Story" to FromSeriesSeason,
                    "たまこまーけっと" to null,
                    "たまこラブストーリー" to FromSeriesSeason,
                )
            }
        }
    }

    @TestFactory
    fun `exclude main subject when playing movie`() = runDynamicTests {
        addSimpleMediaSelectorTest(
            "玉子市场剧场版",
            {
                initSubject("玉子市场 剧场版") {
                    aliases(
                        "玉子爱情故事",
                        "Tamako Love Story",
                        "たまこラブストーリー",
                    )
                    seriesInfo(seasonSort = 1) {
                        series(

                            "玉子市场",
                            "Tamako Market",
                            "たまこまーけっと",
                        )
                    }
                }
            },
        ) {
            checkSubjectExclusion {
                expect(
                    "玉子市场" to FromSeriesSeason,
                    "玉子市场 剧场版" to null,
                    "玉子爱情故事" to null,
                    "Tamako Market" to FromSeriesSeason,
                    "Tamako Love Story" to null,
                    "たまこまーけっと" to FromSeriesSeason,
                    "たまこラブストーリー" to null,
                )
            }
        }
    }
}