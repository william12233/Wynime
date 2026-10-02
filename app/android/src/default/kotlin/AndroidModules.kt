/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.him188.ani.android.navigation.AndroidBrowserNavigator
import me.him188.ani.android.provider.ExternalContentProviderFactoryImpl
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.cache.engine.HttpMediaCacheEngine
import me.him188.ani.app.domain.media.cache.storage.MediaSaveDirProvider
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.resolver.AndroidWebMediaResolver
import me.him188.ani.app.domain.media.resolver.HttpStreamingMediaResolver
import me.him188.ani.app.domain.media.resolver.LocalFileMediaResolver
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.mediasource.web.AndroidOnnxImageCaptchaRecognizer
import me.him188.ani.app.domain.mediasource.web.captcha.AndroidCaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.ImageCaptchaRecognizer
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.platform.AndroidContextFiles
import me.him188.ani.app.platform.AniComponentActivity
import me.him188.ani.app.platform.AppTerminator
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.files
import me.him188.ani.app.platform.findActivity
import me.him188.ani.app.tools.update.AndroidUpdateInstaller
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.app.ui.exprovider.ExternalContentProviderFactory
import me.him188.ani.utils.httpdownloader.HttpDownloader
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File
import kotlin.system.exitProcess

/**
 * 手机 flavor 专属绑定; 与 TV 共用的绑定见交集源集 `src/main` 的 [getCommonAndroidModules].
 */
fun getAndroidModules() = module {
    single<BrowserNavigator> { AndroidBrowserNavigator() }
    single<CaptchaBrowserFactory> { AndroidCaptchaBrowserFactory(androidContext()) }
    single<ImageCaptchaRecognizer> { AndroidOnnxImageCaptchaRecognizer() }

    single<MediaSaveDirProvider> {
        val context = androidContext()
        val defaultBaseMediaCacheDir = context.files.defaultMediaCacheBaseDir.absolutePath

        // 如果外部目录没 mounted, 那也要使用内部目录
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
            dao = get<AniDatabase>().httpCacheDownloadStateDao(),
            mediaSourceId = MediaDownloadManager.LOCAL_FS_MEDIA_SOURCE_ID,
            downloader = get<HttpDownloader>(),
            saveDir = saveDir,
            mediaResolver = get<MediaResolver>(),
        )
    }

    factory<MediaResolver> {
        MediaResolver.from(
            listOf<MediaResolver>(LocalFileMediaResolver())
                .plus(HttpStreamingMediaResolver())
                .plus(
                    AndroidWebMediaResolver(
                        get<MediaSourceManager>().webVideoMatcherLoader,
                        get<SettingsRepository>(),
                        get<WebSessionManager>(),
                    ),
                ),
        )
    }
    single<UpdateInstaller> { AndroidUpdateInstaller() }

    single<AppTerminator> {
        object : AppTerminator {
            override fun exitApp(context: ContextMP, status: Int): Nothing {
                runBlocking(Dispatchers.Main.immediate) {
                    (context.findActivity() as? AniComponentActivity)?.finishAffinity()
                    exitProcess(status)
                }
            }
        }
    }

    single<ExternalContentProviderFactory> {
        ExternalContentProviderFactoryImpl(get())
    }
}
