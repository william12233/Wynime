package com.wynime.app.videoplayer.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.AssHandlerConfig
import io.github.peerless2012.ass.media.kt.withAssMkvSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.exoplayer.ExoPlayerAudioTimeStretch
import org.openani.mediamp.exoplayer.ExoPlayerMediampPlayer
import org.openani.mediamp.io.SeekableInput
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.SeekableInputMediaData
import org.openani.mediamp.source.UriMediaData
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds

@OptIn(InternalForInheritanceMediampApi::class)
@AndroidxOptIn(UnstableApi::class)
class LibassExoPlayerMediampPlayer private constructor(
    parentCoroutineContext: CoroutineContext,
    private val pipeline: LibassMediaSourcePipeline,
    internal val exoMediampPlayer: ExoPlayerMediampPlayer,
) : MediampPlayer by exoMediampPlayer {

    constructor(
        context: Context,
        parentCoroutineContext: CoroutineContext,
        audioTimeStretch: ExoPlayerAudioTimeStretch = ExoPlayerAudioTimeStretch.HighQualityWsola,
        configurePlayerBuilder: ((ExoPlayer.Builder) -> Unit)? = null,
    ) : this(context, parentCoroutineContext, audioTimeStretch, configurePlayerBuilder, LibassMediaSourcePipeline(context))

    private constructor(
        context: Context,
        parentCoroutineContext: CoroutineContext,
        audioTimeStretch: ExoPlayerAudioTimeStretch,
        configurePlayerBuilder: ((ExoPlayer.Builder) -> Unit)?,
        pipeline: LibassMediaSourcePipeline,
    ) : this(
        parentCoroutineContext,
        pipeline,
        ExoPlayerMediampPlayer(
            context,
            parentCoroutineContext,
            audioTimeStretch,
            mediaSourceInterceptor = pipeline::intercept,
            configurePlayerBuilder = configurePlayerBuilder,
        ),
    )

    internal val assHandler: AssHandler get() = pipeline.assHandler

    private val exoPlayer: ExoPlayer get() = exoMediampPlayer.impl
    private val backgroundScope = CoroutineScope(
        parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job.Key]),
    )
    private var closed = false

    init {
        assHandler.init(exoPlayer)
        backgroundScope.launch(Dispatchers.Main.immediate) {
            while (isActive) {

                assHandler.videoTime = exoPlayer.currentPosition * 1_000
                delay(16.milliseconds)
            }
        }
    }

    override suspend fun setMediaData(data: MediaData, playWhenReady: Boolean, startPositionMillis: Long) {

        val playerData = if (data is SeekableInputMediaData) {
            TrackingSeekableInputMediaData(data)
        } else {
            data
        }
        exoMediampPlayer.setMediaData(playerData, playWhenReady, startPositionMillis)
    }

    override val mediaData: StateFlow<MediaData?> = object : StateFlow<MediaData?> {
        override val value: MediaData? get() = exoMediampPlayer.mediaData.value.unwrapTracking()
        override val replayCache: List<MediaData?> get() = listOf(value)
        override suspend fun collect(collector: FlowCollector<MediaData?>): Nothing =
            exoMediampPlayer.mediaData.collect(
                FlowCollector { value -> collector.emit(value.unwrapTracking()) },
            )
    }

    override fun seekTo(positionMillis: Long) {
        exoMediampPlayer.seekTo(positionMillis)

        val positionUs = positionMillis * 1_000
        assHandler.videoTime = positionUs

        assHandler.videoTimeCallback?.invoke(positionUs)
    }

    override fun skip(deltaMillis: Long) {

        seekTo(currentPositionMillis.value + deltaMillis)
    }

    override fun close() {
        if (closed) return
        closed = true
        backgroundScope.cancel()
        exoPlayer.removeListener(assHandler)
        assHandler.release()
        exoMediampPlayer.close()
    }
}

@AndroidxOptIn(UnstableApi::class)
private class LibassMediaSourcePipeline(
    private val context: Context,
) {
    val assHandler = AssHandler(
        renderType = AssRenderType.OVERLAY_OPEN_GL,
        config = AssHandlerConfig(maxRenderPixels = 1920 * 1080),
    )
    private val subtitleParserFactory = AssSubtitleParserFactory(assHandler)
    private val extractorsFactory = DefaultExtractorsFactory()
        .withAssMkvSupport(subtitleParserFactory, assHandler)

    fun intercept(defaultSource: MediaSource, data: MediaData): MediaSource =
        createLibassMediaSource(data) ?: defaultSource

    private fun createLibassMediaSource(data: MediaData): MediaSource? {
        val dataSourceFactory = when (data) {
            is UriMediaData -> DefaultHttpDataSource.Factory()
                .setUserAgent(data.headers["User-Agent"] ?: DEFAULT_USER_AGENT)
                .setDefaultRequestProperties(data.headers)
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MILLIS)

            is SeekableInputMediaData -> {
                if (data.uri.startsWith("file://")) {
                    DefaultDataSource.Factory(context)
                } else {

                    val tracking = data as? TrackingSeekableInputMediaData ?: return null
                    val primaryInput = tracking.primaryInput ?: return null
                    RoutingDataSourceFactory(
                        mediaUri = data.uri,
                        mediaDataSourceFactory = DataSource.Factory {
                            VideoDataDataSource(tracking.source, primaryInput)
                        },
                        fallbackDataSourceFactory = DefaultDataSource.Factory(context),
                    )
                }
            }
        }

        val mediaItem = MediaItem.Builder()
            .setUri(data.playbackUri)
            .setSubtitleConfigurations(
                data.extraFiles.subtitles.mapIndexed { index, subtitle ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.uri)).apply {
                        setId("wynime-external-subtitle-$index")
                        subtitle.label?.let(::setLabel)
                        subtitle.mimeType?.let(::setMimeType)
                        subtitle.language?.let(::setLanguage)
                    }.build()
                },
            )
            .build()

        return DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setSubtitleParserFactory(subtitleParserFactory)
            .createMediaSource(mediaItem)
    }

    private val MediaData.playbackUri: String
        get() = when (this) {
            is UriMediaData -> uri
            is SeekableInputMediaData -> uri
        }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 30_000
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3"
    }
}

private fun MediaData?.unwrapTracking(): MediaData? =
    (this as? TrackingSeekableInputMediaData)?.source ?: this

@OptIn(ExperimentalMediampApi::class)
private class TrackingSeekableInputMediaData(
    val source: SeekableInputMediaData,
) : SeekableInputMediaData by source {
    var primaryInput: SeekableInput? = null
        private set

    override suspend fun createInput(coroutineContext: CoroutineContext): SeekableInput =
        source.createInput(coroutineContext).also { input ->
            if (primaryInput == null) {
                primaryInput = input
            }
        }
}

@AndroidxOptIn(UnstableApi::class)
private class RoutingDataSourceFactory(
    private val mediaUri: String,
    private val mediaDataSourceFactory: DataSource.Factory,
    private val fallbackDataSourceFactory: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = RoutingDataSource(
        mediaUri,
        mediaDataSourceFactory,
        fallbackDataSourceFactory,
    )
}

@AndroidxOptIn(UnstableApi::class)
private class RoutingDataSource(
    private val mediaUri: String,
    private val mediaDataSourceFactory: DataSource.Factory,
    private val fallbackDataSourceFactory: DataSource.Factory,
) : DataSource {
    private val transferListeners = mutableListOf<TransferListener>()
    private var activeDataSource: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        check(activeDataSource == null) { "Data source is already open" }
        val dataSource = if (dataSpec.uri.toString() == mediaUri) {
            mediaDataSourceFactory.createDataSource()
        } else {
            fallbackDataSourceFactory.createDataSource()
        }
        transferListeners.forEach(dataSource::addTransferListener)
        activeDataSource = dataSource
        return dataSource.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(activeDataSource) { "Data source is not open" }.read(buffer, offset, length)

    override fun getUri(): Uri? = activeDataSource?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        activeDataSource?.responseHeaders.orEmpty()

    override fun close() {
        activeDataSource?.close()
        activeDataSource = null
    }
}

class LibassExoPlayerMediampPlayerFactory(
    private val enableHighQualityAudioTimeStretch: () -> Boolean = { true },
) : MediampPlayerFactory<LibassExoPlayerMediampPlayer> {
    override val forClass: KClass<LibassExoPlayerMediampPlayer>
        get() = LibassExoPlayerMediampPlayer::class

    override fun create(
        context: Any,
        parentCoroutineContext: CoroutineContext,
    ): LibassExoPlayerMediampPlayer {
        require(context is Context) { "The context argument must be android.content.Context on Android" }
        val audioTimeStretch = if (enableHighQualityAudioTimeStretch()) {
            ExoPlayerAudioTimeStretch.HighQualityWsola
        } else {
            ExoPlayerAudioTimeStretch.Media3Default
        }
        return LibassExoPlayerMediampPlayer(
            context,
            parentCoroutineContext,
            audioTimeStretch,
            configurePlayerBuilder = { builder ->
                builder.setLoadControl(wynimeExoPlayerLoadControl())
            },
        )
    }
}
