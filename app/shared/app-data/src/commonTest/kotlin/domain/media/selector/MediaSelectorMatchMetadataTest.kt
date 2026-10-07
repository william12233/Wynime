package com.wynime.app.domain.media.selector

import kotlinx.coroutines.flow.first
import com.wynime.app.domain.media.selector.MatchMetadata.EpisodeMatchKind
import com.wynime.app.domain.media.selector.MatchMetadata.SubjectMatchKind
import com.wynime.app.domain.media.selector.testFramework.SimpleMediaSelectorTestSuite
import com.wynime.app.domain.media.selector.testFramework.addSimpleMediaSelectorTest
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.test.TestContainer
import com.wynime.test.TestFactory
import com.wynime.test.runDynamicTests
import kotlin.test.assertEquals
import kotlin.test.fail

@TestContainer
class MediaSelectorMatchMetadataTest {
    @TestFactory
    fun `subjectMatchKind EXACT vs FUZZY`() = runDynamicTests {
        addSimpleMediaSelectorTest(
            "subject match kind tests",
            {
                initSubject("Bocchi The Rock") {}
            },
        ) {
            checkMatchMetadata {
                expect(
                    mediaSubjectName = "Bocchi The Rock",
                    episodeRange = EpisodeRange.single(EpisodeSort(1)),
                    subjectMatchKind = SubjectMatchKind.EXACT,
                    episodeMatchKind = EpisodeMatchKind.SORT,
                )
                expect(
                    mediaSubjectName = "Bocchi The Rock 2",
                    episodeRange = EpisodeRange.single(EpisodeSort(2)),
                    subjectMatchKind = SubjectMatchKind.FUZZY,
                    episodeMatchKind = EpisodeMatchKind.NONE,
                )
            }
        }
    }

    @TestFactory
    fun `episodeMatchKind for sorts`() = runDynamicTests {
        addSimpleMediaSelectorTest(
            "episode match kind - sorts",
            {
                initSubject(
                    subjectName = "Test Anime",
                ) {
                    this.episodeSort = EpisodeSort(10)
                }
            },
        ) {
            checkMatchMetadata {
                expect(
                    mediaSubjectName = "Test Anime",
                    episodeRange = EpisodeRange.single(EpisodeSort(10)),
                    subjectMatchKind = SubjectMatchKind.EXACT,
                    episodeMatchKind = EpisodeMatchKind.SORT,
                )

                expect(
                    mediaSubjectName = "Test Anime",
                    episodeRange = EpisodeRange.single(EpisodeSort(11)),
                    subjectMatchKind = SubjectMatchKind.EXACT,
                    episodeMatchKind = EpisodeMatchKind.NONE,
                )
            }
        }
    }

    @TestFactory
    fun `episodeMatchKind for ep`() = runDynamicTests {
        addSimpleMediaSelectorTest(
            "episode match kind - ep",
            {
                initSubject("My OVA") {
                    episodeEp = EpisodeSort("7")
                }
            },
        ) {
            checkMatchMetadata {
                expect(
                    mediaSubjectName = "My OVA",
                    episodeRange = EpisodeRange.single(EpisodeSort("7")),
                    subjectMatchKind = SubjectMatchKind.EXACT,
                    episodeMatchKind = EpisodeMatchKind.EP,
                )

                expect(
                    mediaSubjectName = "My OVA",
                    episodeRange = EpisodeRange.single(EpisodeSort("8")),
                    subjectMatchKind = SubjectMatchKind.EXACT,
                    episodeMatchKind = EpisodeMatchKind.NONE,
                )
            }
        }
    }

    @TestFactory
    fun `subject and episode combined test`() = runDynamicTests {
        addSimpleMediaSelectorTest(
            "subject and episode combined test",
            {
                initSubject("进击的巨人") {
                    episodeSort = EpisodeSort(2)
                }
            },
        ) {
            checkMatchMetadata {
                expect(
                    mediaSubjectName = "进击的巨人",
                    episodeRange = EpisodeRange.single(EpisodeSort(2)),
                    subjectMatchKind = SubjectMatchKind.EXACT,
                    episodeMatchKind = EpisodeMatchKind.SORT,
                )
                expect(
                    mediaSubjectName = "进击的巨人 第二季",
                    episodeRange = EpisodeRange.single(EpisodeSort(2)),
                    subjectMatchKind = SubjectMatchKind.FUZZY,
                    episodeMatchKind = EpisodeMatchKind.SORT,
                )
                expect(
                    mediaSubjectName = "进击的巨人",
                    episodeRange = EpisodeRange.single(EpisodeSort(3)),
                    subjectMatchKind = SubjectMatchKind.EXACT,
                    episodeMatchKind = EpisodeMatchKind.NONE,
                )
            }
        }
    }
}

class MatchMetadataApi(
    private val suite: SimpleMediaSelectorTestSuite,
) {
    private val checks = mutableListOf<MatchCheck>()

    fun expect(
        mediaSubjectName: String,
        episodeRange: EpisodeRange?,
        subjectMatchKind: MatchMetadata.SubjectMatchKind,
        episodeMatchKind: MatchMetadata.EpisodeMatchKind,
    ) {
        val media = suite.media(
            subjectName = mediaSubjectName,
            episodeRange = episodeRange ?: EpisodeRange.single(EpisodeSort(1)),
            kind = MediaSourceKind.WEB,
        )
        suite.mediaApi.addMedia(
            media,
        )
        checks += MatchCheck(
            media.mediaId,
            mediaSubjectName,
            episodeRange ?: EpisodeRange.single(EpisodeSort(1)),
            subjectMatchKind,
            episodeMatchKind,
        )
    }

    @OptIn(UnsafeOriginalMediaAccess::class)
    suspend fun checkAll() {
        if (checks.isEmpty()) return

        val allIncluded = suite.selector.subjectCandidates.first().filterIsInstance<MaybeExcludedMedia.Included>()
        checks.forEach { check ->
            val found = allIncluded.firstOrNull { included ->
                included.original.mediaId == check.mediaId
            } ?: fail(
                "Expected included media with subjectName='${check.mediaSubjectName}' and episodeRange=${check.episodeRange}, " +
                        "but none found in the included list. Possibly it got excluded? ",
            )

            assertEquals(
                check.expectedSubjectMatchKind,
                found.metadata.subjectMatchKind,
                message = "Media subjectName='${found.original.properties.subjectName}'",
            )
            assertEquals(
                check.expectedEpisodeMatchKind,
                found.metadata.episodeMatchKind,
                message = "Media subjectName='${found.original.properties.subjectName}'",
            )
        }
    }
}

private data class MatchCheck(

    val mediaId: String,
    val mediaSubjectName: String,
    val episodeRange: EpisodeRange,

    val expectedSubjectMatchKind: MatchMetadata.SubjectMatchKind,
    val expectedEpisodeMatchKind: MatchMetadata.EpisodeMatchKind,
)

suspend inline fun SimpleMediaSelectorTestSuite.checkMatchMetadata(block: MatchMetadataApi.() -> Unit) {
    return MatchMetadataApi(this).apply(block).checkAll()
}
