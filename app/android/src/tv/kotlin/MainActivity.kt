/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android.tv

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.session.auth.OAuthCallbackRegistry
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.platform.AniComponentActivity
import me.him188.ani.app.platform.navigation.LocalBrowserNavigator
import me.him188.ani.app.ui.foundation.LocalSketch
import me.him188.ani.app.ui.foundation.rememberAniSketchInstance
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.tv.ui.di.TvAppDependencies
import me.him188.ani.tv.ui.foundation.theme.TvApplicationTheme
import me.him188.ani.tv.ui.main.TvAniAppContent
import org.koin.android.ext.android.getKoin

/**
 * TV 单 Activity: 横屏 (manifest 声明)、singleTask、Compose 全屏.
 *
 * M2: handleStartIntent 解析 `ani://subjects/<id>` deep link -> navigateSubjectDetails.
 */
class MainActivity : AniComponentActivity() {

    private val aniNavigator = AniNavigator()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStartIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleStartIntent(intent)
        // 全面屏: 内容画到系统栏后面 (对齐参考版沉浸效果)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // Resolve application services before entering composition.
        val dependencies = TvAppDependencies.fromKoin(getKoin())
        val imageLoaderClient = getKoin().get<HttpClientProvider>().get(ScopedHttpClientUserAgent.ANI)
        val browserNavigator = getKoin().get<BrowserNavigator>()
        val themeSettings = dependencies.settingsRepository.themeSettings.flow
        val uiSettings = dependencies.settingsRepository.uiSettings.flow
        setContent {
            val theme by themeSettings.collectAsStateWithLifecycle(ThemeSettings.Default)
            val appearance by uiSettings.collectAsStateWithLifecycle(UISettings.Default)
            TvApplicationTheme(theme.seedColor, appearance.appLanguage?.toLanguageTag()) {
                // 与手机 AniApp 同款 Sketch 装配 (§5.6; main 已从 coil 迁移至 sketch)
                val sketch = rememberAniSketchInstance(imageLoaderClient)
                val toaster = remember {
                    // TV 端 Toaster: 原生 Toast (10-foot 下自绘胶囊 M4 视觉阶段再换, §5.3)
                    object : Toaster {
                        override fun toast(text: String) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                CompositionLocalProvider(
                    LocalSketch provides sketch,
                    LocalBrowserNavigator provides browserNavigator,
                    LocalToaster provides toaster,
                ) {
                    TvAniAppContent(aniNavigator, dependencies)
                }
            }
        }
    }

    private fun handleStartIntent(intent: Intent) {
        val data = intent.data ?: return
        val isHttpsCallback = data.scheme == OAUTH_CALLBACK_SCHEME_HTTPS &&
            data.host == OAUTH_CALLBACK_HOST &&
            data.path == OAUTH_CALLBACK_PATH
        val isSchemeCallback = data.scheme == OAUTH_CALLBACK_SCHEME &&
            data.host == OAUTH_CALLBACK_HOST_SCHEME
        if (!isHttpsCallback && !isSchemeCallback) return

        val state = data.getQueryParameter("state")
        val ticket = data.getQueryParameter("ticket")
        val error = data.getQueryParameter("error")
        if (state.isNullOrBlank() || (ticket.isNullOrBlank() && error.isNullOrBlank())) return
        lifecycleScope.launch {
            OAuthCallbackRegistry.publish(state, ticket, error)
        }
    }

    private companion object {
        const val OAUTH_CALLBACK_SCHEME_HTTPS = "https"
        const val OAUTH_CALLBACK_SCHEME = "ani"
        const val OAUTH_CALLBACK_HOST = "wynime-bangumi-broker.wzhou785.workers.dev"
        const val OAUTH_CALLBACK_PATH = "/app/oauth-complete"
        const val OAUTH_CALLBACK_HOST_SCHEME = "bangumi-oauth-callback"
    }
}
