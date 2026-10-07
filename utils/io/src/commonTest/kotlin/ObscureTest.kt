package com.wynime.utils.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObscureTest {

    @Test
    fun `roundtrip ascii password`() {
        val plain = "hunter2!"
        val obscured = obscure(plain)
        assertEquals(plain, tryReveal(obscured))
    }

    @Test
    fun `roundtrip unicode`() {
        val plain = "密码@💀"
        val obscured = obscure(plain)
        assertEquals(plain, tryReveal(obscured))
    }

    @Test
    fun `roundtrip long`() {
        val plain = "a".repeat(2048)
        val obscured = obscure(plain)
        assertEquals(plain, tryReveal(obscured))
    }

    @Test
    fun `obscure produces magic prefix`() {
        assertTrue(obscure("anything").startsWith(MAGIC_PREFIX))
    }

    @Test
    fun `obscure is non-deterministic across IVs`() {

        val plain = "same-input"
        val a = obscure(plain)
        val b = obscure(plain)
        assertNotEquals(a, b)
        assertEquals(plain, tryReveal(a))
        assertEquals(plain, tryReveal(b))
    }

    @Test
    fun `empty string passes through both directions`() {
        assertEquals("", obscure(""))
        assertEquals("", tryReveal(""))
    }

    @Test
    fun `tryReveal returns null for legacy plaintext`() {

        assertNull(tryReveal("not-obscured"))
        assertNull(tryReveal("ob1"))
        assertNull(tryReveal("password123"))
    }

    @Test
    fun `tryReveal throws on prefixed but malformed payload`() {

        assertFails { tryReveal("$MAGIC_PREFIX!!!not-base64!!!") }
        assertFails { tryReveal("${MAGIC_PREFIX}aGk") }
    }

    @Test
    fun `secureRandomBytes returns requested size and is non-zero`() {
        val out = secureRandomBytes(32)
        assertEquals(32, out.size)
        assertTrue(out.any { it != 0.toByte() })
    }
}
