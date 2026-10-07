package com.wynime.app.data.persistent.database

import com.wynime.app.data.models.subject.SubjectTmdbArt
import com.wynime.app.data.models.subject.TmdbImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubjectTmdbArtConverterTest {
    private val converter = ProtoConverters.SubjectTmdbArtConverter

    private fun roundTrip(art: SubjectTmdbArt?): SubjectTmdbArt? =
        converter.fromByteArray(converter.fromSubjectTmdbArt(art))

    @Test
    fun `full art survives round trip`() {
        val art = SubjectTmdbArt(
            backdrops = listOf(TmdbImage("b1-m", "b1-l"), TmdbImage("b2-m", "b2-l")),
            posters = mapOf("ja" to TmdbImage("p-m", "p-l"), "xx" to TmdbImage("px-m", "px-l")),
            logos = mapOf("zh" to TmdbImage("l-m", "l-l", vector = "l.svg")),
        )
        assertEquals(art, roundTrip(art))
        assertEquals("b1-m", roundTrip(art)?.primaryBackdrop?.medium)
    }

    @Test
    fun `empty art survives round trip`() {
        assertEquals(SubjectTmdbArt(), roundTrip(SubjectTmdbArt()))
        assertNull(roundTrip(SubjectTmdbArt())?.primaryBackdrop)
    }

    @Test
    fun `null stays null`() {
        assertNull(converter.fromSubjectTmdbArt(null))
        assertNull(roundTrip(null))
    }
}
