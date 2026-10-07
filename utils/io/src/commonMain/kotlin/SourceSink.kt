package com.wynime.utils.io

import kotlinx.io.Sink
import kotlinx.io.Source

fun Source.copyTo(out: Sink) {
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val bytesRead = this.readAtMostTo(buffer, 0, buffer.size)
        if (bytesRead == -1) break
        out.write(buffer, startIndex = 0, endIndex = bytesRead)
    }
}
