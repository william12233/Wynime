package com.wynime.datasources.api.source

import io.ktor.utils.io.core.Closeable
import com.wynime.utils.logging.thisLogger

fun Closeable.asAutoCloseable() = AutoCloseable { close() }

abstract class HttpMediaSource : MediaSource {
    private val closeables = mutableListOf<AutoCloseable>()

    val logger = thisLogger()

    fun addCloseable(closeable: AutoCloseable) {
        closeables.add(closeable)
    }

    override fun close() {
        super.close()
        this.closeables.forEach { it.close() }
    }

    companion object
}

