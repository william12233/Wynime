@file:Suppress("SSBasedInspection")

package com.wynime.app.domain.foundation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.SendCountExceedException
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ConvertSendCountExceedExceptionFeatureHandlerTest {

    private fun buildTestClient(
        installFeature: Boolean,
        maxSendCount: Int = 2,
        respondBlock: suspend (callCount: Int) -> Pair<HttpStatusCode, String>
    ): HttpClient {
        var callCounter = 0

        val mockEngine = MockEngine { _ ->
            callCounter++
            val (status, content) = respondBlock(callCounter)
            respond(
                content = content,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }

        val client = HttpClient(mockEngine) {
            install(HttpSend) {
                this.maxSendCount = maxSendCount
            }
        }

        if (installFeature) {

            ConvertSendCountExceedExceptionFeatureHandler.applyToClient(
                client,
                value = true,
            )
        }

        return client
    }

    @Test
    fun `without feature - SendCountExceedException is thrown as-is`() = runTest {

        val client = buildTestClient(installFeature = false, maxSendCount = 2) { callCount ->

            HttpStatusCode.Found to "fail #$callCount"
        }

        val ex = assertFailsWith<SendCountExceedException> {
            client.get("https://test.com/alwaysFail")
        }

        assertEquals(
            "Max send count 2 exceeded. Consider increasing the property maxSendCount if more is required.",
            ex.message,
        )
    }

    @Test
    fun `with feature on - SendCountExceedException is converted to IOException`() = runTest {
        val client = buildTestClient(installFeature = true, maxSendCount = 2) { callCount ->

            HttpStatusCode.Found to "fail #$callCount"
        }

        val ex = assertFailsWith<IOException> {
            client.get("https://test.com/alwaysFail")
        }

        assertIs<SendCountExceedException>(
            ex.findCause<SendCountExceedException>(),
            "Expected cause to be SendCountExceedException",
        )
    }

    @Test
    fun `with feature on - successful request does NOT throw`() = runTest {
        var failCount = 0

        val client = buildTestClient(installFeature = true, maxSendCount = 2) { callCount ->
            if (callCount == 1) {
                failCount++
                HttpStatusCode.Found to "fail #$callCount"
            } else {
                HttpStatusCode.OK to "success on retry"
            }
        }

        val response = client.request("https://test.com/flakyEndpoint")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("success on retry", response.bodyAsText())

        assertEquals(1, failCount, "Should fail exactly once, then succeed")
    }

    @Test
    fun `with feature off - normal success`() = runTest {
        val client = buildTestClient(installFeature = false, maxSendCount = 2) { _ ->
            HttpStatusCode.OK to "ok"
        }
        val response = client.get("https://test.com/works")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }
}

private inline fun <reified E : Throwable> Throwable.findCause(): E? {
    var current: Throwable? = this
    while (current != null) {
        if (current is E) {
            return current
        }
        current = current.cause
    }
    return null
}
