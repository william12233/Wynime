package com.wynime.app.domain.sourceplugin

import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.paging.awaitFinished
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.source.plugin.api.ResolvedMedia
import com.wynime.source.plugin.api.ResolvedMediaFormat
import com.wynime.source.plugin.api.SourceChannel
import com.wynime.source.plugin.api.SourceChannelEpisodes
import com.wynime.source.plugin.api.SourceConnectionState
import com.wynime.source.plugin.api.SourceConnectionStatus
import com.wynime.source.plugin.api.SourceEpisode
import com.wynime.source.plugin.api.SourcePlugin
import com.wynime.source.plugin.api.SourcePluginMetadata
import com.wynime.source.plugin.api.SourcePluginPlatform
import com.wynime.source.plugin.api.SourceResolveRequest
import com.wynime.source.plugin.api.SourceSearchRequest
import com.wynime.source.plugin.api.SourceSubject
import com.wynime.source.plugin.api.SourceSubjectDetails
import com.wynime.source.plugin.api.SourceWebResourceMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class SourcePluginMediaSourceTest {
    private val traditionalTitle = "遭到流放的轉生重騎士憑藉遊戲知識大開無雙"
    private val simplifiedTitle = "遭到流放的转生重骑士凭借游戏知识大开无双"

    @Test
    fun `browse search tries traditional and simplified queries`() = runTest {
        val plugin = RecordingPlugin(simplifiedTitle)
        val source = SourcePluginMediaSource(plugin)

        val subjects = source.searchSubjects(traditionalTitle)

        assertEquals(listOf(traditionalTitle, simplifiedTitle), plugin.searchQueries)
        assertEquals(listOf("3410"), subjects.map { it.url.substringBefore('#').substringAfterLast('/') })
    }

    @Test
    fun `browse search continues when one spelling is rejected`() = runTest {
        val plugin = RecordingPlugin(
            title = simplifiedTitle,
            failedQueries = setOf(traditionalTitle),
        )
        val source = SourcePluginMediaSource(plugin)

        val subjects = source.searchSubjects(traditionalTitle)

        assertEquals(listOf(traditionalTitle, simplifiedTitle), plugin.searchQueries)
        assertEquals(listOf("3410"), subjects.map { it.url.substringBefore('#').substringAfterLast('/') })
    }

    @Test
    fun `media discovery tries traditional and simplified queries and matches the title`() = runTest {
        val plugin = RecordingPlugin(simplifiedTitle)
        val source = SourcePluginMediaSource(plugin)

        val media = source.fetch(
            MediaFetchRequest(
                subjectId = "",
                episodeId = "203536",
                subjectNameCN = traditionalTitle,
                subjectNames = emptyList(),
                episodeSort = EpisodeSort("14"),
                episodeName = "第14集",
            ),
        )
        val matches = media.results.toList()
        media.awaitFinished()

        assertEquals(listOf(traditionalTitle, simplifiedTitle), plugin.searchQueries)
        assertEquals(listOf(simplifiedTitle), matches.map { it.media.properties.subjectName })
    }

    @Test
    fun `media discovery keeps the CN name when source names already contain another spelling`() = runTest {
        val targetTitle = "新网球王子 U-17 世界杯 SEMIFINAL"
        val plugin = RecordingPlugin(
            title = targetTitle,
            searchResultsByQuery = mapOf(
                "新テニスの王子様" to listOf(SourceSubject("5107", targetTitle)),
            ),
        )
        val source = SourcePluginMediaSource(plugin)

        val media = source.fetch(
            MediaFetchRequest(
                subjectId = "439197",
                episodeId = "8",
                subjectNameCN = "新网球王子 U-17 世界杯 半决赛",
                subjectNames = listOf("新テニスの王子様 U-17 WORLD CUP SEMIFINAL"),
                episodeSort = EpisodeSort("8"),
                episodeName = "第8集",
            ),
        )
        val matches = media.results.toList()
        media.awaitFinished()

        assertEquals(targetTitle, matches.single().media.properties.subjectName)
        assertTrue(plugin.searchQueries.contains("新テニスの王子様"))
    }

    @Test
    fun `seasonal browse search starts with base and stops after safe match`() = runTest {
        val plugin = RecordingPlugin(
            title = "大王饶命3",
            searchResultsByQuery = mapOf(
                "大王饶命" to listOf(SourceSubject("season-3", "大王饶命3")),
            ),
        )
        val source = SourcePluginMediaSource(plugin)

        val subjects = source.searchSubjects("大王饒命第三季")

        assertEquals(listOf("大王饒命", "大王饶命"), plugin.searchQueries)
        assertEquals(listOf("大王饶命3"), subjects.map { it.name })
    }

    @Test
    fun `nonmatching seasonal result does not stop fallback queries`() = runTest {
        val plugin = RecordingPlugin(
            title = "大王饶命3",
            searchResultsByQuery = mapOf(
                "大王饶命" to listOf(SourceSubject("season-2", "大王饶命2")),
                "大王饶命第三季" to listOf(SourceSubject("season-3", "大王饶命第三季")),
            ),
        )
        val source = SourcePluginMediaSource(plugin)

        val media = source.fetch(
            MediaFetchRequest(
                subjectId = "",
                episodeId = "203536",
                subjectNameCN = "大王饒命第三季",
                subjectNames = emptyList(),
                episodeSort = EpisodeSort("14"),
                episodeName = "第14集",
            ),
        )
        media.results.toList()
        media.awaitFinished()

        assertEquals(
            listOf("大王饒命", "大王饶命", "大王饒命第三季", "大王饶命第三季"),
            plugin.searchQueries,
        )
    }

    @Test
    fun `seasonal discovery reaches a compact Arabic season result`() = runTest {
        val compactTitle = "大王饶命3"
        val plugin = RecordingPlugin(
            title = compactTitle,
            searchResultsByQuery = mapOf(
                compactTitle to listOf(SourceSubject("season-3", compactTitle)),
            ),
        )
        val source = SourcePluginMediaSource(plugin)

        val media = source.fetch(
            MediaFetchRequest(
                subjectId = "",
                episodeId = "203536",
                subjectNameCN = "大王饒命第三季",
                subjectNames = emptyList(),
                episodeSort = EpisodeSort("14"),
                episodeName = "第14集",
            ),
        )
        media.results.toList()
        media.awaitFinished()

        assertTrue(plugin.searchQueries.contains(compactTitle))
    }

    @Test
    fun `arc discovery uses a generic base fallback without losing specificity`() = runTest {
        val targetTitle = "新网球王子 U-17 世界杯 SEMIFINAL"
        val plugin = RecordingPlugin(
            title = targetTitle,
            searchResultsByQuery = mapOf(
                "新网球王子" to listOf(
                    SourceSubject("2022", "新网球王子 U-17世界杯篇"),
                    SourceSubject("5107", targetTitle),
                ),
            ),
        )
        val source = SourcePluginMediaSource(plugin)

        val media = source.fetch(
            MediaFetchRequest(
                subjectId = "439197",
                episodeId = "",
                subjectNameCN = "新网球王子 U-17 世界杯 半决赛",
                subjectNames = emptyList(),
                episodeSort = EpisodeSort("8"),
                episodeName = "第8集",
            ),
        )
        val matches = media.results.toList()
        media.awaitFinished()

        assertEquals(targetTitle, matches.single().media.properties.subjectName)
        assertTrue(plugin.searchQueries.contains("新网球王子"))
    }

    @Test
    fun `specific discovery still accepts an unmarked source title when it is the only safe fallback`() {
        val selected = selectBestSourceSubject(
            subjects = listOf(SourceSubject("base", "新网球王子")),
            queryNames = listOf("新网球王子 U-17 世界杯 半决赛"),
        )

        assertEquals("base", selected?.subject?.id)
    }

    @Test
    fun `specific discovery rejects another marked arc instead of guessing`() {
        val selected = selectBestSourceSubject(
            subjects = listOf(SourceSubject("2022", "新网球王子 U-17世界杯篇")),
            queryNames = listOf("新网球王子 U-17 世界杯 半决赛"),
        )

        assertNull(selected)
    }

    @Test
    fun `unmarked fallback does not stop discovery before another title spelling`() = runTest {
        val targetTitle = "新テニスの王子様 U-17 WORLD CUP SEMIFINAL"
        val plugin = RecordingPlugin(
            title = targetTitle,
            searchResultsByQuery = mapOf(
                "新网球王子" to listOf(SourceSubject("base", "新网球王子")),
                "新テニスの王子様" to listOf(SourceSubject("5107", targetTitle)),
            ),
        )
        val source = SourcePluginMediaSource(plugin)

        val media = source.fetch(
            MediaFetchRequest(
                subjectId = "",
                episodeId = "",
                subjectNameCN = "新网球王子 U-17 世界杯 半决赛",
                subjectNames = listOf(
                    "新网球王子 U-17 世界杯 半决赛",
                    targetTitle,
                ),
                episodeSort = EpisodeSort("8"),
                episodeName = "第8集",
            ),
        )
        val matches = media.results.toList()
        media.awaitFinished()

        assertEquals(targetTitle, matches.single().media.properties.subjectName)
        assertTrue(plugin.searchQueries.contains("新テニスの王子様"))
    }

    @Test
    fun `arc fallback does not invent another marker for an unrelated subject`() = runTest {
        val plugin = RecordingPlugin(title = "新网球王子 U-17世界杯篇")
        val source = SourcePluginMediaSource(plugin)

        source.searchSubjects("新网球王子 U-17 世界杯")

        assertTrue(plugin.searchQueries.none { it == "半决赛" || it == "SEMIFINAL" })
    }

    private class RecordingPlugin(
        private val title: String,
        private val searchResultsByQuery: Map<String, List<SourceSubject>> = emptyMap(),
        private val failedQueries: Set<String> = emptySet(),
        val searchQueries: MutableList<String> = mutableListOf(),
    ) : SourcePlugin {
        override val metadata = SourcePluginMetadata(
            id = "next",
            displayName = "Next",
            version = "1.0.27",
            website = "https://next.xifanacg.com",
            pluginApiVersion = 3,
            minHostVersion = "0.1.3",
            supportedPlatforms = setOf(SourcePluginPlatform.DESKTOP, SourcePluginPlatform.ANDROID),
        )

        override suspend fun checkConnection() = SourceConnectionStatus(SourceConnectionState.CONNECTED)

        override suspend fun search(request: SourceSearchRequest): List<SourceSubject> {
            searchQueries += request.query
            if (request.query in failedQueries) error("query spelling rejected")
            return searchResultsByQuery[request.query]
                ?: if (request.query == title) {
                    listOf(SourceSubject("3410", title, detailUrl = "https://next.xifanacg.com/anime/3410"))
                } else {
                    emptyList()
                }
        }

        override suspend fun getSubject(subjectId: String) = SourceSubjectDetails(
            subject = SourceSubject(subjectId, title, detailUrl = "https://next.xifanacg.com/anime/$subjectId"),
            channels = listOf(
                SourceChannelEpisodes(
                    channel = SourceChannel("xfxf1", "稀飯新番主線-1"),
                    episodes = listOf(
                        SourceEpisode(
                            id = "203536",
                            displayName = "第14集",
                            episodeSort = 14f,
                            playPageUrl = "https://next.xifanacg.com/anime/3410/play/203536?source=xfxf1",
                        ),
                    ),
                ),
            ),
        )

        override suspend fun resolve(request: SourceResolveRequest) = ResolvedMedia(
            stableIdentity = "next-3410-xfxf1-203536",
            url = "https://cdn.example/next.mp4",
            format = ResolvedMediaFormat.MP4,
        )

        override fun matchWebResource(url: String) = SourceWebResourceMatch.Continue

        override fun close() = Unit
    }
}
