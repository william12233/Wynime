import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReleaseArtifactNamesTest {
    @Test
    fun `official release assets have a fixed two-file allowlist`() {
        assertEquals(
            setOf(
                "wynime-0.1.3-arm64-v8a.apk",
                "wynime-0.1.3-windows-x86_64.zip",
            ),
            ReleaseArtifactNames.officialReleaseAssets("0.1.3"),
        )
        assertEquals("arm64-v8a", ReleaseArtifactNames.officialAndroidArch)
        assertEquals("windows", ReleaseArtifactNames.officialWindowsOs)
        assertEquals("x86_64", ReleaseArtifactNames.officialWindowsArch)
        assertTrue(ReleaseArtifactNames.isOfficialReleaseTag("v0.1.3"))
        assertTrue(!ReleaseArtifactNames.isOfficialReleaseTag("0.1.3-dev"))
    }
}
