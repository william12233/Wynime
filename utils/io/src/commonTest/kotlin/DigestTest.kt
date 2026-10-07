package com.wynime.utils.io

import kotlinx.io.Buffer
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.write
import kotlin.test.Test
import kotlin.test.assertEquals

class DigestTest {

    @Test
    fun `md5 - hello world`() {
        val data = "Hello, world!"
        val expectedHex = "6cd3556deb0da54bca060b4c39479839"

        val byteString = data.encodeToByteString()
        val actualByteStringDigest = byteString.digest(DigestAlgorithm.MD5).toHexString()
        assertEquals(expectedHex, actualByteStringDigest)

        val buffer = Buffer().apply { write(byteString) }
        val actualSourceDigest = buffer.readAndDigest(DigestAlgorithm.MD5).toHexString()
        assertEquals(expectedHex, actualSourceDigest)
    }

    @Test
    fun `sha1 - hello world`() {
        val data = "Hello, world!"
        val expectedHex = "943a702d06f34599aee1f8da8ef9f7296031d699"

        val byteString = data.encodeToByteString()
        assertEquals(expectedHex, byteString.digest(DigestAlgorithm.SHA1).toHexString())

        val buffer = Buffer().apply { write(byteString) }
        assertEquals(expectedHex, buffer.readAndDigest(DigestAlgorithm.SHA1).toHexString())
    }

    @Test
    fun `sha256 - hello world`() {
        val data = "Hello, world!"
        val expectedHex = "315f5bdb76d078c43b8ac0064e4a0164612b1fce77c869345bfc94c75894edd3"

        val byteString = data.encodeToByteString()
        assertEquals(expectedHex, byteString.digest(DigestAlgorithm.SHA256).toHexString())

        val buffer = Buffer().apply { write(byteString) }
        assertEquals(expectedHex, buffer.readAndDigest(DigestAlgorithm.SHA256).toHexString())
    }
}
