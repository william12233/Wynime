package com.wynime.utils.platform

import kotlin.jvm.JvmInline
import kotlin.random.Random

internal expect class UuidDelegate

@JvmInline
expect value class Uuid internal constructor(
    internal val delegate: UuidDelegate
) {
    override fun toString(): String

    companion object {
        fun random(random: Random = Random): Uuid
        fun randomString(random: Random = Random): String
    }
}

internal fun generateRandomUuid(random: Random = Random): String {
    val randomBytes = ByteArray(16)
    random.nextBytes(randomBytes)

    randomBytes[6] = (randomBytes[6].toInt() and 0x0F or 0x40).toByte()

    randomBytes[8] = (randomBytes[8].toInt() and 0x3F or 0x80).toByte()

    fun byteArrayToHex(byteArray: ByteArray): String {
        val hexChars = "0123456789abcdef".toCharArray()
        val result = StringBuilder(byteArray.size * 2)

        byteArray.forEachIndexed { index, byte ->
            val intVal = byte.toInt() and 0xff
            result.append(hexChars[intVal shr 4])
            result.append(hexChars[intVal and 0x0f])

            if (index == 3 || index == 5 || index == 7 || index == 9) {
                result.append('-')
            }
        }

        return result.toString()
    }

    return byteArrayToHex(randomBytes)
}
