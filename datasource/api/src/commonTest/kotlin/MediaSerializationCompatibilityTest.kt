package com.wynime.datasources.api

import com.wynime.datasources.api.topic.ResourceLocation
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MediaSerializationCompatibilityTest {
    @Test
    fun `previous persisted plugin resource is readable after namespace migration`() {
        val payload = """{"type":"me.him188.ani.datasources.api.topic.ResourceLocation.SourcePluginMedia","pluginId":"eacg","subjectId":"subject","channelId":"channel","episodeId":"episode","uri":"https://source.test/video"}"""
        val resource = assertIs<ResourceLocation.SourcePluginMedia>(Json.decodeFromString<ResourceLocation>(payload))
        assertEquals("eacg", resource.pluginId)
        assertEquals("episode", resource.episodeId)
        assertTrue(Json.encodeToString<ResourceLocation>(resource).contains("me.him188.ani.datasources.api.topic.ResourceLocation.SourcePluginMedia"))
    }

    @Test
    fun `previous episode sort discriminator retains its value`() {
        val sort = Json.decodeFromString<EpisodeSort>("""{"type":"me.him188.ani.datasources.api.EpisodeSort.Normal","number":12.5}""")
        assertEquals(12.5f, sort.number)
        assertTrue(Json.encodeToString<EpisodeSort>(sort).contains("me.him188.ani.datasources.api.EpisodeSort.Normal"))
    }
}
