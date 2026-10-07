package com.wynime.tools.datasourcetestmcp.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.features.Screenshots
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurface
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData
import java.awt.Dimension
import java.io.File
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import kotlin.coroutines.coroutineContext

class MpvVideoAnalyzer {
    class Output(
        val analysis: MediaAnalysisResult,
        val frames: List<CapturedFrame>,
    )

    suspend fun analyze(input: ProbeVideoInput): Output {
        runCatching {
            val nativeDir = System.getProperty("wynime.mpv.native.dir")
            if (nativeDir != null) {
                MPVHandle.setRuntimeLibraryDirectory(nativeDir, extractRuntimeLibrary = false)
            } else {
                MPVHandle.useDefaultRuntimeLibraryDirectory()
            }
            MPVHandle.setLogHandler { msg ->
                if (msg.level <= 30) println("[mpv/" + msg.prefix + "] " + msg.line)
            }
        }.onFailure { exception ->
            if (exception is CancellationException) throw exception
            return Output(
                MediaAnalysisResult(
                    available = false,
                    errors = listOf(
                        "mpv 原生库加载失败: ${exception.message.orEmpty()}. " +
                                "請使用 Windows 原生程式庫，或設定 -Dwynime.mpv.native.dir 指定目錄",
                    ),
                ),
                emptyList(),
            )
        }

        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "mpv-video-analyzer").apply { isDaemon = true }
        }
        return try {
            withContext(executor.asCoroutineDispatcher()) {
                analyzeWithPlayer(input)
            }
        } finally {
            executor.shutdown()
        }
    }

    private suspend fun analyzeWithPlayer(input: ProbeVideoInput): Output {
        val errors = mutableListOf<String>()
        val frames = mutableListOf<CapturedFrame>()
        val player = MpvMediampPlayer(Any(), coroutineContext)
        val handle = player.impl as MPVHandle
        val window = if (input.showWindow) createWindow(player, input.videoUrl) else null
        return try {
            val playbackTest = runPlaybackTest(player, handle, input, errors, frames)

            withTimeoutOrNull(10_000) {
                while (handle.getPropertyInt("width") <= 0) {
                    delay(200)
                }
            }

            val width = handle.getPropertyInt("width").takeIf { it > 0 }
            val height = handle.getPropertyInt("height").takeIf { it > 0 }
            val videoCodec = handle.getPropertyString("video-format")
            val audioCodec = handle.getPropertyString("audio-codec-name")
            val fps = handle.getPropertyDouble("container-fps").takeIf { it > 0 }
            val videoBitrate = handle.getPropertyInt("video-bitrate").takeIf { it > 0 }?.toLong()
            val audioBitrate = handle.getPropertyInt("audio-bitrate").takeIf { it > 0 }?.toLong()
            val sampleRate = handle.getPropertyInt("audio-params/samplerate").takeIf { it > 0 }
            val channels = handle.getPropertyInt("audio-params/channel-count").takeIf { it > 0 }
            val hwdec = handle.getPropertyString("hwdec-current")

            val durationMillis = player.getCurrentMediaProperties()?.durationMillis?.takeIf { it > 0 }

            Output(
                MediaAnalysisResult(
                    available = true,
                    tool = "wynime-player (mpv, hwdec=${hwdec ?: "no"})",
                    durationSeconds = durationMillis?.let { it / 1000.0 },
                    overallBitrate = listOfNotNull(videoBitrate, audioBitrate).takeIf { it.isNotEmpty() }?.sum(),
                    video = if (videoCodec != null || width != null) {
                        VideoStreamInfo(
                            codec = videoCodec?.lowercase(),
                            width = width,
                            height = height,
                            frameRate = fps?.let { "%.3f".format(it).trimEnd('0').trimEnd('.') },
                            bitrate = videoBitrate,
                        )
                    } else null,
                    audio = if (audioCodec != null) {
                        AudioStreamInfo(
                            codec = audioCodec.lowercase(),
                            sampleRate = sampleRate?.toString(),
                            channels = channels,
                            bitrate = audioBitrate,
                        )
                    } else null,
                    playback = playbackTest,
                    errors = errors,
                ),
                frames,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Output(
                MediaAnalysisResult(
                    available = true,
                    tool = "wynime-player (mpv)",
                    errors = errors + "${e::class.simpleName}: ${e.message.orEmpty()}",
                ),
                frames,
            )
        } finally {
            runCatching { player.close() }
            window?.let { w -> SwingUtilities.invokeLater { w.dispose() } }
        }
    }

    private suspend fun runPlaybackTest(
        player: MpvMediampPlayer,
        handle: MPVHandle,
        input: ProbeVideoInput,
        errors: MutableList<String>,
        frames: MutableList<CapturedFrame>,
    ): PlaybackTestResult {
        fun polledPositionMillis(): Long {
            val timePos = runCatching { handle.getPropertyDouble("time-pos") }.getOrDefault(0.0)
            val fromProperty = if (timePos.isFinite() && timePos > 0) (timePos * 1000).toLong() else 0L
            return maxOf(fromProperty, player.getCurrentPositionMillis())
        }
        val openStart = System.currentTimeMillis()
        try {
            player.setMediaData(UriMediaData(input.videoUrl, input.headers, MediaExtraFiles()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors += "打开媒体失败: ${e::class.simpleName}: ${e.message.orEmpty()}"
            return PlaybackTestResult(
                ran = true,
                ok = false,
                requestedSeconds = input.playSeconds,
                finalState = player.getCurrentPlaybackState().toString(),
                errors = errors.toList(),
                openMillis = System.currentTimeMillis() - openStart,
            )
        }
        val openMillis = System.currentTimeMillis() - openStart

        val resumeAt = System.currentTimeMillis()
        player.resume()

        val state = withTimeoutOrNull(input.playTimeoutMillis) {
            player.playbackState.first {
                it == PlaybackState.PLAYING || it == PlaybackState.ERROR || it == PlaybackState.FINISHED
            }
        }
        if (state != PlaybackState.PLAYING) {
            errors += when (state) {
                null -> "等待进入播放状态超时 (${input.playTimeoutMillis}ms)"
                else -> "未能进入播放状态: $state"
            }
            return PlaybackTestResult(
                ran = true,
                ok = false,
                requestedSeconds = input.playSeconds,
                playedPositionMillis = polledPositionMillis(),
                finalState = (state ?: player.getCurrentPlaybackState()).toString(),
                errors = errors.toList(),
                openMillis = openMillis,
            )
        }

        val timeToFirstFrameMillis = withTimeoutOrNull<Long?>(input.playTimeoutMillis) {
            while (polledPositionMillis() <= 0) {
                if (player.getCurrentPlaybackState() == PlaybackState.ERROR) return@withTimeoutOrNull null
                delay(50)
            }
            System.currentTimeMillis() - resumeAt
        }
        val timeToPlayingMillis = timeToFirstFrameMillis

        val framesDir = input.captureFramesDir?.let { File(it).apply { mkdirs() } }
        if (framesDir != null) {
            captureFrame(player, framesDir, "first_frame", polledPositionMillis())?.let { frames += it }
        }

        var bufferingCount = 0
        var bufferingTotalMillis = 0L
        val targetPositionMillis = input.playSeconds.coerceIn(1, 60) * 1000L
        val playStart = System.currentTimeMillis()
        val reached = coroutineScope {
            val bufferingTracker = launch {
                var enteredAt = 0L
                player.playbackState.collect { st ->
                    val now = System.currentTimeMillis()
                    if (st == PlaybackState.PAUSED_BUFFERING) {
                        bufferingCount++
                        enteredAt = now
                    } else if (enteredAt != 0L) {
                        bufferingTotalMillis += now - enteredAt
                        enteredAt = 0L
                    }
                }
            }

            val remainingCaptures = if (framesDir != null) {
                input.captureAtSeconds.filter { it >= 0 }.sorted().toMutableList()
            } else {
                mutableListOf()
            }
            try {
                withTimeoutOrNull<Boolean?>(input.playTimeoutMillis) {
                    while (true) {
                        val pos = polledPositionMillis()
                        while (framesDir != null && remainingCaptures.isNotEmpty() &&
                            pos >= remainingCaptures.first() * 1000L
                        ) {
                            val sec = remainingCaptures.removeAt(0)
                            captureFrame(player, framesDir, "frame_%02ds".format(sec), pos)?.let { frames += it }
                        }
                        when {
                            pos >= targetPositionMillis -> return@withTimeoutOrNull true
                            player.getCurrentPlaybackState() == PlaybackState.ERROR -> return@withTimeoutOrNull false

                            pos > 0 && runCatching { handle.getPropertyBoolean("eof-reached") }
                                .getOrDefault(false) -> return@withTimeoutOrNull true

                            else -> delay(100)
                        }
                    }
                    @Suppress("UNREACHABLE_CODE")
                    null
                }
            } finally {
                bufferingTracker.cancel()
            }
        }
        val playWallClockMillis = System.currentTimeMillis() - playStart

        if (framesDir != null && reached == true) {
            captureFrame(player, framesDir, "mid", polledPositionMillis())?.let { frames += it }
        }

        if (reached != true) {
            errors += when (reached) {
                false -> "播放中途出错 (state=ERROR)"
                else -> "播放 ${input.playSeconds}s 超时: 位置停在 ${polledPositionMillis()}ms"
            }
        }
        return PlaybackTestResult(
            ran = true,
            ok = reached == true,
            requestedSeconds = input.playSeconds,
            playedPositionMillis = polledPositionMillis(),
            finalState = player.getCurrentPlaybackState().toString(),
            errors = if (reached == true) emptyList() else errors.toList(),
            openMillis = openMillis,
            timeToPlayingMillis = timeToPlayingMillis,
            timeToFirstFrameMillis = timeToFirstFrameMillis,
            playWallClockMillis = playWallClockMillis,
            bufferingCount = bufferingCount,
            bufferingTotalMillis = bufferingTotalMillis,
        )
    }

    private suspend fun captureFrame(
        player: MpvMediampPlayer,
        dir: File,
        label: String,
        positionMillis: Long,
    ): CapturedFrame? {
        val file = dir.resolve("$label.png")
        player.features[Screenshots.Key]?.takeScreenshot(file.absolutePath) ?: return null
        repeat(25) {
            if (file.isFile && file.length() > 0) {
                return CapturedFrame(
                    positionMillis = positionMillis,
                    path = file.absolutePath,
                    label = label,
                )
            }
            delay(200)
        }
        return null
    }

    private fun createWindow(player: MpvMediampPlayer, videoUrl: String): ComposeWindow {
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow().apply {
                title = "Wynime 数据源测试 - probe_video (mpv)"
                size = Dimension(960, 600)
                setLocationRelativeTo(null)
                setContent { ProbeWindowContent(player, videoUrl) }
                isVisible = true
            }
        }
        return window
    }
}

@Composable
private fun ProbeWindowContent(player: MpvMediampPlayer, videoUrl: String) {
    val state by player.playbackState.collectAsState()
    val properties by player.mediaProperties.collectAsState()
    val position by player.currentPositionMillis.collectAsState()

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MpvMediampPlayerSurface(player, Modifier.fillMaxSize())
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(12.dp)) {
            Text(
                "probe_video: 用 Wynime 播放器 (mpv) 真实播放测试",
                color = Color.White,
                fontSize = 13.sp,
            )
            Text(
                videoUrl,
                color = Color(0xFF9E9E9E),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "state=$state  position=${position / 1000.0}s  duration=${(properties?.durationMillis ?: 0) / 1000.0}s",
                color = Color(0xFF80CBC4),
                fontSize = 12.sp,
            )
        }
    }
}
