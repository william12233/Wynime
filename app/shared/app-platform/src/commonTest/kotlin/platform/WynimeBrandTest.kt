package com.wynime.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class WynimeBrandTest {
    @Test
    fun `release tag URLs include the v prefix`() {
        assertEquals(
            "https://github.com/william12233/Wynime/releases/tag/v1.1.0",
            WynimeBrand.githubReleaseTagPrefix + "1.1.0",
        )
    }
}
