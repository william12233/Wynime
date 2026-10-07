package com.wynime.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.media.hls.HlsPlaybackPreparer
import com.wynime.app.domain.media.hls.PlatformHlsPlaybackPreparer
import com.wynime.app.platform.AndroidPermissionManager
import com.wynime.app.platform.PermissionManager
import com.wynime.app.videoplayer.media.LibassExoPlayerMediampPlayerFactory
import org.koin.dsl.module
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.MediampPlayerFactoryLoader
import org.openani.mediamp.compose.MediampPlayerSurfaceProviderLoader
import org.openani.mediamp.exoplayer.compose.ExoPlayerMediampPlayerSurfaceProvider

@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
fun getCommonAndroidModules(coroutineScope: CoroutineScope) = module {
    single<PermissionManager> {
        AndroidPermissionManager()
    }
    single<HlsPlaybackPreparer> { PlatformHlsPlaybackPreparer(get()) }

    single<MediampPlayerFactory<*>> {
        val videoScaffoldConfig = get<SettingsRepository>().videoScaffoldConfig
        MediampPlayerFactoryLoader.register(
            LibassExoPlayerMediampPlayerFactory {

                runBlocking { videoScaffoldConfig.flow.first().enableHighQualityAudioTimeStretch }
            },
        )
        MediampPlayerSurfaceProviderLoader.register(ExoPlayerMediampPlayerSurfaceProvider())
        MediampPlayerFactoryLoader.first()
    }
}
