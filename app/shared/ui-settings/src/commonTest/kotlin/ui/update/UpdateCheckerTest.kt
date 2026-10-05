/*
 * Copyright (C) 2026 Wynime contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/william12233/Wynime/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.network.protocol.ReleaseClass
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateCheckerTest {
    @Test
    fun `stable channel selects the newest stable release and matching Windows asset`() = runTest {
        val checker = checker(
            """
            [
              {"tag_name":"0.3-beta1","prerelease":true,"published_at":"2026-10-03T00:00:00Z","assets":[{"name":"wynime-0.3-beta1-windows-x86_64.zip","browser_download_url":"https://example.invalid/beta.zip"}]},
              {"tag_name":"0.2","prerelease":false,"published_at":"2026-10-04T00:00:00Z","body":"- stable","assets":[{"name":"wynime-0.2-windows-x86_64.zip","browser_download_url":"https://example.invalid/stable.zip"}]},
              {"tag_name":"0.1","prerelease":false,"published_at":"2026-10-01T00:00:00Z","assets":[{"name":"wynime-0.1-windows-x86_64.zip","browser_download_url":"https://example.invalid/old.zip"}]}
            ]
            """.trimIndent(),
            Platform.Windows(Arch.X86_64),
        )

        val result = checker.checkLatestVersion(ReleaseClass.STABLE, currentVersion = "0.1")

        assertEquals("0.2", result?.name)
        assertEquals(listOf("https://example.invalid/stable.zip"), result?.downloadUrlAlternatives)
        assertEquals("- stable", result?.changelogs?.single()?.changes)
    }

    @Test
    fun `beta channel accepts stable release but excludes a missing asset`() = runTest {
        val checker = checker(
            """
            [
              {"tag_name":"0.2-beta1","prerelease":true,"assets":[]},
              {"tag_name":"0.2","prerelease":false,"assets":[]}
            ]
            """.trimIndent(),
            Platform.Android(Arch.ARMV8A),
        )

        assertNull(checker.checkLatestVersion(ReleaseClass.BETA, currentVersion = "0.1"))
    }

    @Test
    fun `non-success response and malformed JSON are treated as no update`() = runTest {
        val notFound = checker("{}", Platform.Windows(Arch.X86_64), HttpStatusCode.NotFound)
        assertNull(notFound.checkLatestVersion(ReleaseClass.STABLE, currentVersion = "0.1"))

        val malformed = checker("not-json", Platform.Windows(Arch.X86_64))
        assertNull(malformed.checkLatestVersion(ReleaseClass.STABLE, currentVersion = "0.1"))
    }

    private fun checker(
        body: String,
        platform: Platform,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): UpdateChecker {
        val client = HttpClient(MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        return UpdateChecker(
            client = client.asScopedHttpClient(),
            releasesUrl = "https://example.invalid/releases",
            platformProvider = { platform },
        )
    }
}
