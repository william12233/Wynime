package com.wynime.app.domain.sourceplugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import com.wynime.source.plugin.api.SourceResultStatus

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

    @Test
    fun `linkage and plugin boundary cast failures are plugin errors`() {
        val linkage = sourcePluginBoundaryFailure(
            traceId = "trace-linkage",
            provider = "provider",
            entryPoint = "SEARCH_REQUEST",
            fallbackStatus = SourceResultStatus.PARSE_ERROR,
            error = NoSuchMethodError("legacy constructor"),
            url = "https://example.com/search",
            retryable = false,
        )
        val contract = sourcePluginBoundaryFailure(
            traceId = "trace-contract",
            provider = "provider",
            entryPoint = "SEARCH_REQUEST",
            fallbackStatus = SourceResultStatus.PARSE_ERROR,
            error = ClassCastException("plugin contract"),
            url = "https://example.com/search",
            retryable = false,
        )

        assertEquals(SourceResultStatus.PLUGIN_ERROR, linkage.status)
        assertEquals(SourceResultStatus.PLUGIN_ERROR, contract.status)
    }

    @Test
    fun `cancellation is never converted into source failure`() {
        assertFailsWith<CancellationException> {
            sourcePluginBoundaryFailure(
                traceId = "trace-cancel",
                provider = "provider",
                entryPoint = "SEARCH_REQUEST",
                fallbackStatus = SourceResultStatus.PARSE_ERROR,
                error = CancellationException("cancelled"),
                url = null,
                retryable = false,
            )
        }
    }
}
