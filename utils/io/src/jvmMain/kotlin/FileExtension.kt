package com.wynime.utils.io

import java.io.File
import java.io.RandomAccessFile
import java.util.ArrayDeque

fun File.readLastNLines(n: Int): List<String> {
    if (!exists() || !isFile || n <= 0) {
        return emptyList()
    }

    RandomAccessFile(this, "r").use { raf ->
        val fileLength = raf.length()
        if (fileLength == 0L) return emptyList()

        var pos = fileLength - 1

        while (pos >= 0) {
            raf.seek(pos)
            val c = raf.readByte()
            if (c != '\n'.code.toByte() && c != '\r'.code.toByte()) {
                break
            }
            pos--
        }

        if (pos < 0) {
            return emptyList()
        }

        val lines = ArrayDeque<String>(n)
        val sb = StringBuilder()
        var lineCount = 0

        while (pos >= 0 && lineCount < n) {
            raf.seek(pos)
            val c = raf.readByte().toInt().toChar()
            pos--

            if (c == '\n' || c == '\r') {

                if (sb.isNotEmpty()) {
                    sb.reverse()
                    lines.addFirst(sb.toString())
                    sb.setLength(0)
                    lineCount++
                }
            } else {

                sb.append(c)
            }
        }

        if (sb.isNotEmpty()) {
            sb.reverse()
            lines.addFirst(sb.toString())
        }

        return lines.toList()
    }
}
