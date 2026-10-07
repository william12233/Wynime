package com.wynime.utils.ktor

import com.wynime.utils.ktor.HttpTokenChecker.isValidToken
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpTokenCheckerTest {
    @Test
    fun `test valid token`() {
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        assertTrue(isValidToken(token))
    }

    @Test
    fun `test valid token with special characters`() {
        val token = "token-123_ABC~xyz.456"
        assertTrue(isValidToken(token))
    }

    @Test
    fun `test empty token`() {
        val token = ""
        assertFalse(isValidToken(token))
    }

    @Test
    fun `test token with forbidden characters`() {
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9@#$$%%"
        assertFalse(isValidToken(token))
    }

    @Test
    fun `test token with spaces`() {
        val token = "eyJhbGciOiJI UzI1NiIsInR5cCI6IkpXVCJ9"
        assertFalse(isValidToken(token))
    }

    @Test
    fun `test token exceeding max length`() {
        val token = "a".repeat(256)
        assertFalse(isValidToken(token))
    }

    @Test
    fun `test token at max length`() {
        val token = "a".repeat(255)
        assertTrue(isValidToken(token))
    }

    @Test
    fun `chinese is not valid`() {
        assertFalse(isValidToken("柚"))
    }

    @Test
    fun `chinese unicode is not valid`() {
        assertFalse(isValidToken("\u67DA"))
    }

    @Test
    fun `test token with only allowed special characters`() {
        val token = "-_.~"
        assertTrue(isValidToken(token))
    }
}