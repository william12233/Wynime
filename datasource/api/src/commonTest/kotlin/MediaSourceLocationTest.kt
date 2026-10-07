package com.wynime.datasources.api

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import com.wynime.datasources.api.source.MediaSourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MediaSourceLocationTest {
    @Test
    fun `locations initialized before deserialization retain their wire names`() {
        val locations = listOf(MediaSourceLocation.Online, MediaSourceLocation.Lan, MediaSourceLocation.Local)
        assertEquals(locations, MediaSourceLocation.entries)
        for ((location, name) in locations.zip(listOf("ONLINE", "LAN", "LOCAL"))) {
            val encoded = "\"$name\""
            assertEquals(encoded, Json.encodeToString<MediaSourceLocation>(location))
            assertEquals(location, Json.decodeFromString<MediaSourceLocation>(encoded))
        }
    }

    @Test
    fun `unknown location is rejected`() {
        assertFailsWith<SerializationException> { Json.decodeFromString<MediaSourceLocation>("\"UNKNOWN\"") }
    }
}
