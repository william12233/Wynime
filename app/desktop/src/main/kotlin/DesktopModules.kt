package com.wynime.app.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.wynime.app.data.persistent.dataStores
import com.wynime.app.data.persistent.database.WynimeDatabase
import com.wynime.app.data.repository.WindowStateRepository
import com.wynime.app.data.repository.WindowStateRepositoryImpl
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.foundation.get
import com.wynime.app.domain.media.cache.engine.HttpMediaCacheEngine
import com.wynime.app.domain.media.cache.storage.MediaSaveDirProvider
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.domain.media.hls.HlsPlaybackPreparer
import com.wynime.app.domain.media.hls.PlatformHlsPlaybackPreparer
import com.wynime.app.domain.media.resolver.DesktopWebMediaResolver
import com.wynime.app.domain.media.resolver.HttpStreamingMediaResolver
import com.wynime.app.domain.media.resolver.LocalFileMediaResolver
import com.wynime.app.domain.media.resolver.MediaResolver
import com.wynime.app.domain.sourceplugin.SourcePluginMediaResolver
import com.wynime.app.domain.mediasource.web.DesktopOnnxImageCaptchaRecognizer
import com.wynime.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
import com.wynime.app.domain.mediasource.web.captcha.DesktopCaptchaBrowserFactory
import com.wynime.app.domain.mediasource.web.captcha.ImageCaptchaRecognizer
import com.wynime.app.domain.mediasource.web.captcha.WebSessionManager
import com.wynime.app.navigation.BrowserNavigator
import com.wynime.app.navigation.DesktopBrowserNavigator
import com.wynime.app.platform.AppTerminator
import com.wynime.app.platform.Context
import com.wynime.app.platform.DefaultAppTerminator
import com.wynime.app.platform.DesktopContext
import com.wynime.app.platform.GrantedPermissionManager
import com.wynime.app.platform.PermissionManager
import com.wynime.app.platform.files
import com.wynime.app.tools.update.DesktopUpdateInstaller
import com.wynime.app.tools.update.UpdateInstaller
import com.wynime.app.videoplayer.player.WynimeMpvMediampPlayerFactory
import com.wynime.utils.httpdownloader.HttpDownloader
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.toKtPath
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import org.koin.dsl.module
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.MediampPlayerFactoryLoader
import org.openani.mediamp.compose.MediampPlayerSurfaceProviderLoader
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurfaceProvider
import java.io.File
import kotlin.io.path.Path

fun getDesktopModules(getContext: () -> DesktopContext, scope: CoroutineScope) = module {
    single<Context> { getContext() }

    single<MediaSaveDirProvider> {
        val settings = get<SettingsRepository>().mediaCacheSettings
        val defaultMediaCachePath = getContext().files.defaultMediaCacheBaseDir

        val baseSaveDir = runBlocking {
            val saveDirSettings = settings.flow.first().saveDir

            if (saveDirSettings == null) {
                val finalPathString = defaultMediaCachePath.absolutePath
                settings.update { copy(saveDir = finalPathString) }
                return@runBlocking finalPathString
            }

            if (!File(saveDirSettings).run { canRead() && canWrite() }) {
                val fallbackPathString = defaultMediaCachePath.absolutePath
                settings.update { copy(saveDir = fallbackPathString) }
                return@runBlocking fallbackPathString
            }

            saveDirSettings
        }

        object : MediaSaveDirProvider {
            override val saveDir: String = baseSaveDir
        }
    }

    single<HttpMediaCacheEngine> {
        val saveDir = Path(get<MediaSaveDirProvider>().saveDir).resolve(HttpMediaCacheEngine.MEDIA_CACHE_DIR)
        logger<HttpMediaCacheEngine>().info { "HttpMediaCacheEngine base save dir: $saveDir" }

        HttpMediaCacheEngine(
            dao = get<WynimeDatabase>().httpCacheDownloadStateDao(),
            mediaSourceId = MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID,
            downloader = get<HttpDownloader>(),
            saveDir = saveDir.toKtPath(),
            mediaResolver = get<MediaResolver>(),
        )
    }

    single<MediampPlayerFactory<*>> {
        MediampPlayerFactoryLoader.register(WynimeMpvMediampPlayerFactory())
        MediampPlayerSurfaceProviderLoader.register(MpvMediampPlayerSurfaceProvider())
        MediampPlayerFactoryLoader.first()
    }
    single<BrowserNavigator> { DesktopBrowserNavigator() }
    single<CaptchaBrowserFactory> { DesktopCaptchaBrowserFactory() }
    single<ImageCaptchaRecognizer> { DesktopOnnxImageCaptchaRecognizer() }
    single<HlsPlaybackPreparer> { PlatformHlsPlaybackPreparer(get()) }
    factory<MediaResolver> {
        val webResolver = DesktopWebMediaResolver(
            getContext(),
            get<MediaSourceManager>().webVideoMatcherLoader,
            get<WebSessionManager>(),
        )
        MediaResolver.from(
            listOf<MediaResolver>(LocalFileMediaResolver())
                .plus(SourcePluginMediaResolver(get(), webResolver))
                .plus(HttpStreamingMediaResolver())
                .plus(webResolver),
        )
    }
    single<UpdateInstaller> { DesktopUpdateInstaller.currentOS() }
    single<PermissionManager> { GrantedPermissionManager }
    single<WindowStateRepository> { WindowStateRepositoryImpl(getContext().dataStores.savedWindowStateStore) }
    single<AppTerminator> { DefaultAppTerminator }
}
