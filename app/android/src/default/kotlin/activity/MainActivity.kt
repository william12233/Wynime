package com.wynime.android.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.wynime.app.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.wynime.app.domain.session.auth.OAuthCallbackRegistry
import com.wynime.app.navigation.WynimeNavigator
import com.wynime.app.platform.WynimeComponentActivity
import com.wynime.app.platform.rememberPlatformWindow
import com.wynime.app.ui.exprovider.ExternalContentProviderFactory
import com.wynime.app.ui.exprovider.LocalExternalContentProvider
import com.wynime.app.ui.foundation.layout.LocalPlatformWindow
import com.wynime.app.ui.foundation.theme.SystemBarColorEffect
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.foundation.widgets.Toaster
import com.wynime.app.ui.main.WynimeApp
import com.wynime.app.ui.main.WynimeAppContent
import com.wynime.utils.logging.error
import com.wynime.utils.logging.logger
import org.koin.android.ext.android.inject

class MainActivity : WynimeComponentActivity() {
    private val logger = logger<MainActivity>()
    private val wynimeNavigator = WynimeNavigator()

    private val externalContentProviderFactory: ExternalContentProviderFactory by inject()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleStartIntent(intent)
    }

    private fun handleStartIntent(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme == "https" &&
            data.host == OAUTH_CALLBACK_HOST &&
            data.path == OAUTH_CALLBACK_PATH
        ) {
            publishOAuthCallback(data.getQueryParameter("state"), data.getQueryParameter("ticket"), data.getQueryParameter("error"))
            return
        }

        if (data.scheme != "ani") return
        when (data.host) {
            "bangumi-oauth-callback" -> {
                publishOAuthCallback(data.getQueryParameter("state"), data.getQueryParameter("ticket"), data.getQueryParameter("error"))
            }

            "subjects" -> {
                val id = data.pathSegments.getOrNull(0)?.toIntOrNull() ?: return
                navigateWhenReady("subject details") { navigateSubjectDetails(id, placeholder = null) }
            }

        }
    }

    private fun publishOAuthCallback(state: String?, ticket: String?, error: String?) {
        if (state.isNullOrBlank()) return
        if (ticket.isNullOrBlank() && error.isNullOrBlank()) return
        lifecycleScope.launch {
            try {
                OAuthCallbackRegistry.publish(state, ticket, error)
            } catch (e: Exception) {
                logger.error(e) { "Failed to accept OAuth callback" }
            }
        }
    }

    private fun navigateWhenReady(destination: String, action: WynimeNavigator.() -> Unit) {
        lifecycleScope.launch {
            try {
                if (!wynimeNavigator.isBackStackReady()) {
                    wynimeNavigator.awaitBackStack()
                    delay(1000)
                }
                wynimeNavigator.action()
            } catch (e: Exception) {
                logger.error(e) { "Failed to navigate to $destination" }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleStartIntent(intent)

        enableEdgeToEdge(

            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),

            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val toaster = object : Toaster {
            override fun toast(text: String) {
                Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
            }
        }

        val externalContentProvider = externalContentProviderFactory.create(this, lifecycleScope)

        setContent {
            WynimeApp {
                val externalComponentProviderUpdated by rememberUpdatedState(externalContentProvider)

                SystemBarColorEffect()

                CompositionLocalProvider(
                    LocalToaster provides toaster,
                    LocalPlatformWindow provides rememberPlatformWindow(this),
                    LocalExternalContentProvider provides externalComponentProviderUpdated,
                ) {

                    @OptIn(ExperimentalComposeUiApi::class)
                    val rootModifier = if (BuildConfig.DEBUG) {
                        Modifier.semantics { testTagsAsResourceId = true }
                    } else {
                        Modifier
                    }
                    Box(rootModifier) {
                        WynimeAppContent(wynimeNavigator)
                    }
                }
            }
        }
    }

    private companion object {
        const val OAUTH_CALLBACK_HOST = "wynime-bangumi-broker.wzhou785.workers.dev"
        const val OAUTH_CALLBACK_PATH = "/app/oauth-complete"
    }
}
