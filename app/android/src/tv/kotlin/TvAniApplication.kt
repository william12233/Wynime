/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android.tv

import android.app.Application
import me.him188.ani.android.getCommonAndroidModules
import me.him188.ani.app.platform.AndroidLoggingConfigurator
import me.him188.ani.app.platform.createAppRootCoroutineScope
import me.him188.ani.app.platform.getCommonKoinModule
import me.him188.ani.app.platform.startCommonKoinModule
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * TV variant 的 Application.
 *
 * 与手机 AniApplication 的差异 (flavor 门控):
 * - getCommonKoinModule(enableMediaCache = false): TV 使用与手機一致的通用裝配;
 * - 單進程，使用 TV 專用的瀏覽器與應用終止器;
 * - M0 不接入 Sentry/Firebase (tv classpath 已剔除 firebase, 见 build.gradle.kts).
 */
class TvAniApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val logsDir = filesDir.resolve("logs").absolutePath
        AndroidLoggingConfigurator.configure(logsDir)

        val defaultUEH = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            logger<TvAniApplication>().error(e) { "!!!ANI TV FATAL EXCEPTION!!! ($e)" }
            Thread.sleep(500)
            defaultUEH?.uncaughtException(t, e)
        }

        val scope = createAppRootCoroutineScope()

        val koinApp = startKoin {
            androidContext(this@TvAniApplication)
            // TV 使用不啟用媒體快取的通用裝配。
            modules(getCommonKoinModule({ this@TvAniApplication }, scope, enableMediaCache = false))
            modules(getCommonAndroidModules(scope))
            modules(getTvAndroidModules()) // src/tv — Web 解析链 / BrowserNavigator 降级 / AppTerminator
        }.startCommonKoinModule(this@TvAniApplication, scope)
    }
}
