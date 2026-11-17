package com.wynime.app.domain.media.hls

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException

internal actual val PlatformHlsProxyServerFactory: HlsProxyServerFactory = HlsProxyServerFactory { JvmHlsProxyServer() }

internal class JvmHlsProxyServer : HlsProxyServer {
    private val closed = AtomicBoolean(false)
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val requests = SupervisorJob()
    private val connections = mutableSetOf<Socket>()

    override val port: Int get() = serverSocket.localPort

    override fun start(handler: HlsProxyRequestHandler) {
        thread(name = "HlsProxy-$port", isDaemon = true) {
            while (!closed.get()) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: SocketException) {
                    if (!closed.get()) logger.warn(e) { "Failed to accept HLS proxy connection" }
                    continue
                } catch (e: IOException) {
                    logger.warn(e) { "Failed to accept HLS proxy connection" }
                    continue
                }
                val accepted = synchronized(connections) { !closed.get() && connections.add(socket) }
                if (!accepted) {
                    socket.close()
                    continue
                }
                thread(name = "HlsProxy-$port-conn", isDaemon = true) {
                    try {
                        socket.use { handleConnection(it, handler) }
                    } finally {
                        synchronized(connections) { connections.remove(socket) }
                    }
                }
            }
        }
    }

    private fun handleConnection(socket: Socket, handler: HlsProxyRequestHandler) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
            val request = parseHlsProxyRequest(reader.readLine()) { reader.readLine() } ?: return
            socket.getOutputStream().use { output ->
                try {
                    runBlocking(requests) { handler.handle(request, OutputStreamSink(output)) }
                } catch (_: SocketException) {
                } catch (_: CancellationException) {
                } catch (e: Throwable) {

                    logger.warn(e) { "Failed to serve HLS proxy request ${request.path}" }
                }
                runCatching { output.flush() }
            }
        } catch (e: IOException) {
            if (!closed.get()) logger.warn(e) { "Failed to serve HLS proxy request" }
        }
    }

    private class OutputStreamSink(private val output: OutputStream) : HlsProxyResponseSink {
        override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
            output.write(bytes, offset, length)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            requests.cancel()
            try {
                serverSocket.close()
            } finally {
                val active = synchronized(connections) { connections.toList().also { connections.clear() } }
                active.forEach { socket -> runCatching { socket.close() } }
            }
        }
    }

    private companion object {
        private val logger = logger<JvmHlsProxyServer>()
    }
}

internal actual fun resolveHlsUri(baseUri: String, uri: String): String {
    val parsed = runCatching { URI(uri) }.getOrNull() ?: return uri
    if (parsed.isAbsolute) return uri
    val base = runCatching { URI(baseUri) }.getOrNull() ?: return uri
    return base.resolve(parsed).toString()
}
