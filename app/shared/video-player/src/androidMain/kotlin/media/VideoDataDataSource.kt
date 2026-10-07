@file:androidx.annotation.OptIn(UnstableApi::class)

package com.wynime.app.videoplayer.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.runBlocking
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import org.openani.mediamp.io.SeekableInput
import org.openani.mediamp.source.SeekableInputMediaData
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

@androidx.annotation.OptIn(UnstableApi::class)
class VideoDataDataSource(
    private val videoData: SeekableInputMediaData,
    private val file: SeekableInput,
) : BaseDataSource(true) {
    private companion object {
        @JvmStatic
        private val logger = logger<VideoDataDataSource>()
        private const val ENABLE_READ_LOG = false
        private const val ENABLE_TRACE_LOG = false
    }

    private var uri: Uri? = null

    private var opened = false

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {

        if (length == 0) return 0

        if (ENABLE_READ_LOG) {
            logger.warn { "VideoDataDataSource read: offset=$offset, length=$length" }
        }

        val bytesRead = if (ENABLE_READ_LOG) {
            val (value, time) = measureTimedValue {
                file.read(buffer, offset, length)
            }
            if (time > 100.milliseconds) {
                logger.warn { "VideoDataDataSource slow read: read $offset for length $length took $time" }
            }
            value
        } else {
            file.read(buffer, offset, length)
        }
        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT
        }
        bytesTransferred(bytesRead)
        return bytesRead
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        if (ENABLE_TRACE_LOG) logger.info { "Opening dataSpec, offset=${dataSpec.position}, length=${dataSpec.length}, videoData=$videoData" }

        val uri = dataSpec.uri
        if (opened && dataSpec.uri == this.uri) {
            if (ENABLE_TRACE_LOG) logger.info { "Double open, will not start download." }
        } else {
            this.uri = uri
            transferInitializing(dataSpec)
            opened = true
        }

        val fileLength = videoData.fileLength() ?: 0

        if (ENABLE_TRACE_LOG) logger.info { "fileLength = $fileLength" }

        if (dataSpec.position >= fileLength) {
            if (ENABLE_TRACE_LOG) logger.info { "dataSpec.position ${dataSpec.position} > fileLength $fileLength" }
        } else {
            if (dataSpec.position != -1L && dataSpec.position != 0L) {
                if (ENABLE_TRACE_LOG) logger.info { "Seeking to ${dataSpec.position}" }
                runBlocking { file.seekTo(dataSpec.position) }
            }

            if (ENABLE_TRACE_LOG) logger.info { "Open done, bytesRemaining = ${file.bytesRemaining}" }
        }

        transferStarted(dataSpec)
        return file.bytesRemaining
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (ENABLE_TRACE_LOG) logger.info { "Closing VideoDataDataSource" }
        uri = null
        if (opened) {
            transferEnded()
        }
    }
}
