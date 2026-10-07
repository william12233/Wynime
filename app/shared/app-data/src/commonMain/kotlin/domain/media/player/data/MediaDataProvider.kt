package com.wynime.app.domain.media.player.data

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import com.wynime.app.domain.media.resolver.MediaSourceOpenException
import org.openani.mediamp.io.SeekableInput
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.SeekableInputMediaData
import kotlin.coroutines.cancellation.CancellationException

interface MediaDataProvider<out S : MediaData> {
    val extraFiles: MediaExtraFiles

    @Throws(MediaSourceOpenException::class, CancellationException::class)
    suspend fun open(scopeForCleanup: CoroutineScope): S
}
