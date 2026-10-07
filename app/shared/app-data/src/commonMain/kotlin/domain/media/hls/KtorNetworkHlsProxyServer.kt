package com.wynime.app.domain.media.hls

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

internal class KtorNetworkHlsProxyServer private constructor(
    private val selector: SelectorManager,
    serverSocket: ServerSocket,
) : HlsProxyServer {
    private val closed = atomic(false)

    @Volatile
    private var serverSocket: ServerSocket = serverSocket

    override val port: Int = (serverSocket.localAddress as InetSocketAddress).port

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO_ + CoroutineName("HlsProxy-$port"))

    override fun start(handler: HlsProxyRequestHandler) {
        scope.launch {
            while (isActive && !closed.value) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    if (closed.value) break
                    logger.warn(e) { "HLS proxy listener on port $port failed, rebinding" }
                    rebind()
                    continue
                }
                launch {
                    try {
                        handleConnection(socket, handler)
                    } finally {
                        socket.close()
                    }
                }
            }
        }
    }

    private suspend fun rebind() {
        runCatching { serverSocket.close() }
        try {
            val rebound = aSocket(selector).tcp().bind("127.0.0.1", port) { reuseAddress = true }
            serverSocket = rebound
            if (closed.value) rebound.close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to rebind HLS proxy listener on port $port" }
            delay(ACCEPT_RETRY_DELAY)
        }
    }

    internal fun breakListenerForTest() {
        serverSocket.close()
    }

    private suspend fun handleConnection(socket: Socket, handler: HlsProxyRequestHandler) {
        val input = socket.openReadChannel()
        val output = socket.openWriteChannel(autoFlush = false)
        try {

            val request = withTimeoutOrNull(REQUEST_TIMEOUT) {
                parseHlsProxyRequest(input.readUTF8Line(MAX_LINE_LENGTH)) { input.readUTF8Line(MAX_LINE_LENGTH) }
            } ?: return
            try {
                handler.handle(request, ChannelSink(output))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {

            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!closed.value) logger.warn(e) { "Failed to serve HLS proxy request" }
        } finally {

            runCatching { output.flushAndClose() }
        }
    }

    private class ChannelSink(private val output: ByteWriteChannel) : HlsProxyResponseSink {
        override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
            output.writeFully(bytes, offset, offset + length)
            output.flush()
        }
    }

    override fun close() {
        if (closed.compareAndSet(expect = false, update = true)) {
            scope.cancel()
            runCatching { serverSocket.close() }
            runCatching { selector.close() }
        }
    }

    companion object Factory : HlsProxyServerFactory {
        private val logger = logger<KtorNetworkHlsProxyServer>()
        private val REQUEST_TIMEOUT = 30.seconds
        private val ACCEPT_RETRY_DELAY = 1.seconds
        private const val MAX_LINE_LENGTH = 16 * 1024

        override suspend fun create(): KtorNetworkHlsProxyServer {
            val selector = SelectorManager(Dispatchers.IO_)
            try {
                val serverSocket = aSocket(selector).tcp().bind("127.0.0.1", 0) { reuseAddress = true }
                return KtorNetworkHlsProxyServer(selector, serverSocket)
            } catch (e: Throwable) {
                selector.close()
                throw e
            }
        }
    }
}
