package com.wynime.android

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.wynime.android.navigation.AndroidBrowserNavigator
import com.wynime.android.provider.ExternalContentProviderFactoryImpl
import com.wynime.app.data.persistent.database.WynimeDatabase
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.foundation.get
import com.wynime.app.domain.media.cache.engine.HttpMediaCacheEngine
import com.wynime.app.domain.media.cache.storage.MediaSaveDirProvider
import com.wynime.app.domain.media.download.MediaDownloadManager
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.domain.media.resolver.AndroidWebMediaResolver
import com.wynime.app.domain.media.resolver.HttpStreamingMediaResolver
import com.wynime.app.domain.media.resolver.LocalFileMediaResolver
import com.wynime.app.domain.media.resolver.MediaResolver
import com.wynime.app.domain.sourceplugin.SourcePluginMediaResolver
import com.wynime.app.domain.mediasource.web.AndroidOnnxImageCaptchaRecognizer
import com.wynime.app.domain.mediasource.web.captcha.AndroidCaptchaBrowserFactory
import com.wynime.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
import com.wynime.app.domain.mediasource.web.captcha.ImageCaptchaRecognizer
import com.wynime.app.domain.mediasource.web.captcha.WebSessionManager
import com.wynime.app.navigation.BrowserNavigator
import com.wynime.app.platform.AndroidContextFiles
import com.wynime.app.platform.WynimeComponentActivity
import com.wynime.app.platform.AppTerminator
import com.wynime.app.platform.ContextMP
import com.wynime.app.platform.files
import com.wynime.app.platform.findActivity
import com.wynime.app.tools.update.AndroidUpdateInstaller
import com.wynime.app.tools.update.UpdateInstaller
import com.wynime.app.ui.exprovider.ExternalContentProviderFactory
import com.wynime.utils.httpdownloader.HttpDownloader
import com.wynime.utils.io.absolutePath
import com.wynime.utils.io.resolve
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File
import kotlin.system.exitProcess

fun getAndroidModules() = module {
    single<BrowserNavigator> { AndroidBrowserNavigator() }
    single<CaptchaBrowserFactory> { AndroidCaptchaBrowserFactory(androidContext()) }
    single<ImageCaptchaRecognizer> { AndroidOnnxImageCaptchaRecognizer() }

    single<MediaSaveDirProvider> {
        val context = androidContext()
        val defaultBaseMediaCacheDir = context.files.defaultMediaCacheBaseDir.absolutePath

        val saveDir = if (!defaultBaseMediaCacheDir.startsWith(context.filesDir.absolutePath) &&
            Environment.getExternalStorageState(File(defaultBaseMediaCacheDir)) == Environment.MEDIA_MOUNTED
        ) {
            defaultBaseMediaCacheDir
        } else {
            (context.files as AndroidContextFiles).fallbackInternalBaseMediaCacheDir.absolutePath
        }

        object : MediaSaveDirProvider {
            override val saveDir: String = saveDir
        }
    }

    single<HttpMediaCacheEngine> {
        val logger = logger<HttpMediaCacheEngine>()
        val saveDir = kotlinx.io.files.Path(get<MediaSaveDirProvider>().saveDir)
            .resolve(HttpMediaCacheEngine.MEDIA_CACHE_DIR)
        logger.info { "HttpMediaCacheEngine base save directory: $saveDir" }

        HttpMediaCacheEngine(
            dao = get<WynimeDatabase>().httpCacheDownloadStateDao(),
            mediaSourceId = MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID,
            downloader = get<HttpDownloader>(),
            saveDir = saveDir,
            mediaResolver = get<MediaResolver>(),
        )
    }

    factory<MediaResolver> {
        val webResolver = AndroidWebMediaResolver(
            get<MediaSourceManager>().webVideoMatcherLoader,
            get<SettingsRepository>(),
            get<WebSessionManager>(),
        )
        MediaResolver.from(
            listOf<MediaResolver>(LocalFileMediaResolver())
                .plus(SourcePluginMediaResolver(get(), webResolver))
                .plus(HttpStreamingMediaResolver())
                .plus(webResolver),
        )
    }
    single<UpdateInstaller> { AndroidUpdateInstaller() }

    single<AppTerminator> {
        object : AppTerminator {
            override fun exitApp(context: ContextMP, status: Int): Nothing {
                runBlocking(Dispatchers.Main.immediate) {
                    (context.findActivity() as? WynimeComponentActivity)?.finishAffinity()
                    exitProcess(status)
                }
            }
        }
    }

    single<ExternalContentProviderFactory> {
        ExternalContentProviderFactoryImpl(get())
    }
}
