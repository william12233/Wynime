package com.wynime.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class VersionCodeTest {
    @Test
    fun `versionCode pattern`() {
        val versionCode = currentWynimeBuildConfig.fourDigitVersionCode
        assertEquals(4, versionCode.length)
        assertEquals(
            true,
            versionCode matches Regex("""[0-9]{4}"""),
            message = "$versionCode is not a valid version code",
        )
    }
}
