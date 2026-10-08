package com.wynime.app.platform

import kotlin.test.Test
import kotlin.test.assertTrue

class VersionCodeTest {
    @Test
    fun `versionCode pattern`() {
        val versionCode = currentWynimeBuildConfig.fourDigitVersionCode
        assertTrue(versionCode.length >= 4, "$versionCode is too short to be a valid version code")
        assertTrue(versionCode matches Regex("""[0-9]+"""), "$versionCode is not a valid version code")
    }
}
