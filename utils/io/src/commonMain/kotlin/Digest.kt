package com.wynime.utils.io

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.bytestring.ByteString
import kotlinx.io.write

enum class DigestAlgorithm {
    MD5, SHA256, SHA1
}

const val DEFAULT_BUFFER_SIZE: Int = 8 * 1024

expect fun Source.readAndDigest(algorithm: DigestAlgorithm): ByteArray

fun ByteString.digest(algorithm: DigestAlgorithm): ByteArray = Buffer().apply {
    write(this@digest)
}.readAndDigest(algorithm)
