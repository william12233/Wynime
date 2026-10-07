package com.wynime.app.tools.update

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.wynime.datasources.api.topic.FileSize.Companion.bytes
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.coroutines.cancellableCoroutineScope
import com.wynime.utils.coroutines.withExceptionCollector
import com.wynime.utils.io.DEFAULT_BUFFER_SIZE
import com.wynime.utils.io.DigestAlgorithm
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.bufferedSink
import com.wynime.utils.io.bufferedSource
import com.wynime.utils.io.delete
import com.wynime.utils.io.exists
import com.wynime.utils.io.length
import com.wynime.utils.io.readAndDigest
import com.wynime.utils.io.resolve
import com.wynime.utils.ktor.ScopedHttpClient
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

interface FileDownloader {

    val progress: Flow<Float>
    val state: StateFlow<FileDownloaderState>

    fun reuse(file: SystemPath, url: String, checked: Boolean = true): Boolean = false

    suspend fun download(
        alternativeUrls: List<String>,
        filenameProvider: (url: String) -> String = { it.substringAfterLast("/", "") },
        saveDir: SystemPath,
    ): SystemPath?
}

sealed class FileDownloaderState {

    data object Idle : FileDownloaderState()

    data object Downloading : FileDownloaderState()

    sealed class Completed : FileDownloaderState()

    data class Succeed(
        val url: String,
        val file: SystemPath,
        val checked: Boolean,
    ) : Completed()

    data class Cancelled(val throwable: CancellationException) : Completed()

    data class Failed(val throwable: Throwable) : Completed()
}

class DefaultFileDownloader(
    private val client: ScopedHttpClient,
) : FileDownloader {
    private companion object {
        private val logger = logger<DefaultFileDownloader>()
    }

    override val state = MutableStateFlow<FileDownloaderState>(FileDownloaderState.Idle)

    private val _progress = MutableStateFlow(0f)
    override val progress: Flow<Float> get() = _progress

    override fun reuse(file: SystemPath, url: String, checked: Boolean): Boolean {
        if (!file.exists() || file.length() <= 0) return false
        state.value = FileDownloaderState.Succeed(url, file, checked)
        _progress.value = 1f
        return true
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun download(
        alternativeUrls: List<String>,
        filenameProvider: (url: String) -> String,
        saveDir: SystemPath,
    ): SystemPath? {

        require(alternativeUrls.isNotEmpty()) { "No URLs provided." }

        state.update {
            if (it != FileDownloaderState.Idle && it !is FileDownloaderState.Completed) {
                return null
            }
            FileDownloaderState.Downloading
        }

        _progress.value = 0f
        withExceptionCollector {
            for (url in alternativeUrls) {
                try {
                    val filename = filenameProvider(url)
                    val targetFile = saveDir.resolve(filename)

                    val remoteChecksum = fetchRemoteChecksum(client, url)

                    if (remoteChecksum == null) {

                        logger.info { "No remote SHA-1 found for: $url" }
                    } else {

                        if (targetFile.exists()) {
                            logger.info { "File $filename already exists, size=${targetFile.length().bytes}, verifying SHA-1..." }
                            val localChecksum = computeLocalChecksum(targetFile, DigestAlgorithm.SHA1)
                            if (localChecksum == remoteChecksum) {

                                logger.info { "File $filename already exists and SHA-1 matches. Skipping download." }
                                state.value = FileDownloaderState.Succeed(url, targetFile, checked = true)
                                return targetFile
                            } else {

                                logger.info { "File $filename exists but SHA-1 mismatch. Deleting old file..." }
                                withContext(Dispatchers.IO_) {
                                    targetFile.delete()
                                }
                            }
                        }
                    }

                    tryDownload(client, url, targetFile)

                    if (remoteChecksum != null) {
                        val localChecksum = computeLocalChecksum(targetFile, DigestAlgorithm.SHA1)
                        if (localChecksum != remoteChecksum) {
                            logger.info { "File $filename SHA-1 mismatch after download. Deleting file..." }
                            withContext(Dispatchers.IO_) {
                                targetFile.delete()
                            }
                            state.value = FileDownloaderState.Failed(
                                IllegalStateException("Downloaded file $filename SHA-1 mismatch after download."),
                            )
                            return null
                        }
                    }

                    state.value = FileDownloaderState.Succeed(url, targetFile, checked = remoteChecksum != null)
                    return targetFile

                } catch (e: CancellationException) {

                    state.value = FileDownloaderState.Cancelled(e)
                    throw e
                } catch (e: Throwable) {

                    collect(e)
                    state.value = FileDownloaderState.Failed(getLast()!!)
                }
            }

            throwLast()
        }

        return null
    }

    private suspend fun fetchRemoteChecksum(client: ScopedHttpClient, url: String): String? {
        return try {

            client.use { get("$url.sha1").body<String>().trim() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClientRequestException) {
            if (e.response.status == io.ktor.http.HttpStatusCode.NotFound) {
                logger.info { "No remote SHA-1 found for: $url" }
                null
            } else {
                throw e
            }
        }
    }

    private suspend fun tryDownload(client: ScopedHttpClient, url: String, file: SystemPath) {
        cancellableCoroutineScope {
            logger.info { "Attempting download: $url" }
            try {
                client.use {
                    prepareRequest(url) {
                        timeout {
                            requestTimeoutMillis = 1_000_000
                        }
                    }.execute { resp ->
                        val length = resp.contentLength()
                        logger.info { "Downloading $url to ${file.absolutePath}, length=${(length ?: 0).bytes}" }

                        val downloaded = object {
                            val value = atomic(0L)
                        }

                        val input = resp.bodyAsChannel()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                        if (length != null) {
                            this@cancellableCoroutineScope.launch {
                                while (isActive) {
                                    delay(1.seconds)
                                    _progress.value = downloaded.value.value.toFloat() / length
                                }
                            }
                        }

                        file.bufferedSink().use { output ->
                            while (!input.isClosedForRead) {
                                val read = input.readAvailable(buffer)
                                if (read == -1) {
                                    break
                                }
                                downloaded.value.addAndGet(read.toLong())
                                withContext(Dispatchers.IO_) {
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                        _progress.value = 1f

                        logger.info { "Successfully downloaded: $url" }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.info(e) { "Failed to download $url" }
                throw e
            } finally {

                cancelScope()
            }
        }
    }

    private fun computeLocalChecksum(file: SystemPath, algorithm: DigestAlgorithm): String {
        return file.bufferedSource().use {
            it.readAndDigest(algorithm).toHexString()
        }
    }
}
