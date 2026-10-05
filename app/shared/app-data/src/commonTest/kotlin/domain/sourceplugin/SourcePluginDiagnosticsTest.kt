/*
 * Copyright (C) 2026 OpenAni contributors.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.sourceplugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import me.him188.ani.source.plugin.api.SourceResultStatus

class SourcePluginDiagnosticsTest {
    @Test
    fun `safe source url removes credentials query and fragment`() {
        val safe = safeSourceUrl("https://user:password@example.com/path?token=private#fragment")

        assertEquals("https://example.com/path", safe)
        assertFalse(safe.contains("password"))
        assertFalse(safe.contains("token"))
    }

    @Test
    fun `failure diagnostics retain only safe request metadata`() {
        val diagnostics = sourceFailureDiagnostics(
            traceId = "trace-1",
            provider = "provider",
            entryPoint = "SEARCH_REQUEST",
            status = SourceResultStatus.HTTP_ERROR,
            url = "https://user:password@example.com/path?token=private",
            statusCode = 503,
            failureReason = "HTTP 503",
        )

        assertEquals("https://example.com/path", diagnostics.url)
        assertEquals("example.com", diagnostics.domain)
        assertEquals(503, diagnostics.statusCode)
        assertEquals(SourceResultStatus.HTTP_ERROR, diagnostics.responseCategory)
        assertEquals(emptyList(), diagnostics.cookieNames)
    }
}
