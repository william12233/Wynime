package com.wynime.app.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.wynime.app.data.models.preference.DarkMode
import com.wynime.app.data.models.preference.ThemeSettings
import com.wynime.app.navigation.WynimeNavigator
import com.wynime.app.navigation.LocalNavigator
import com.wynime.app.navigation.MainScreenPage
import com.wynime.app.navigation.NavRoutes
import com.wynime.app.navigation.NoopBrowserNavigator
import com.wynime.app.navigation.rememberWynimeBackStack
import com.wynime.app.platform.navigation.LocalBrowserNavigator
import com.wynime.app.tools.LocalTimeFormatter
import com.wynime.app.tools.TimeFormatter
import com.wynime.app.ui.foundation.animation.ProvideWynimeMotionCompositionLocals
import com.wynime.app.ui.foundation.navigation.LocalOnBackPressedDispatcherOwner
import com.wynime.app.ui.foundation.navigation.OnBackPressedDispatcher
import com.wynime.app.ui.foundation.navigation.OnBackPressedDispatcherOwner
import com.wynime.app.ui.foundation.theme.WynimeTheme
import com.wynime.app.ui.foundation.theme.LocalThemeSettings
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.foundation.widgets.NoOpToaster
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.imageResource

@OptIn(TestOnly::class)
@Composable
inline fun ProvideCompositionLocalsForPreview(
    darkMode: DarkMode = DarkMode.AUTO,
    crossinline content: @Composable () -> Unit,
) {
    val wynimeNavigator = remember { WynimeNavigator() }
    val previewImage = imageResource(Res.drawable.a)
    val viewModelStoreOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(viewModelStoreOwner) {
        onDispose {
            viewModelStoreOwner.viewModelStore.clear()
        }
    }
    CompositionLocalProvider(
        LocalIsPreviewing provides true,
        LocalNavigator providesDefault wynimeNavigator,
        LocalToaster providesDefault NoOpToaster,
        LocalSketch provides rememberWynimePreviewSketch(BitmapPainter(previewImage)),
        LocalImageViewerHandler providesDefault rememberImageViewerHandler(),
        LocalTimeFormatter providesDefault remember { TimeFormatter() },
        LocalOnBackPressedDispatcherOwner provides remember {
            object : OnBackPressedDispatcherOwner {
                override val onBackPressedDispatcher: OnBackPressedDispatcher = OnBackPressedDispatcher(null)
                override val lifecycle: Lifecycle get() = TestGlobalLifecycleOwner.lifecycle
            }
        },
        LocalLifecycleOwner providesDefault remember {
            TestGlobalLifecycleOwner
        },
        LocalThemeSettings providesDefault ThemeSettings.Default.copy(
            darkMode = darkMode,
        ),
        LocalViewModelStoreOwner provides viewModelStoreOwner,
        LocalPlatformFontFamily providesDefault remember {
            PlatformFontFamily(null)
        },
        LocalBrowserNavigator providesDefault NoopBrowserNavigator,
    ) {
        wynimeNavigator.setBackStack(rememberWynimeBackStack(NavRoutes.Main(MainScreenPage.Exploration)))
        ProvidePlatformCompositionLocalsForPreview {
            WynimeTheme(darkModeOverride = darkMode) {
                ProvideWynimeMotionCompositionLocals {
                    content()
                }
            }
        }
    }
}

@TestOnly
data object TestGlobalLifecycleOwner : LifecycleOwner {
    override val lifecycle: Lifecycle by lazy {
        LifecycleRegistry.createUnsafe(this).apply {
            this.currentState = Lifecycle.State.RESUMED
        }
    }
}

@TestOnly
@Composable
@PublishedApi
internal expect inline fun ProvidePlatformCompositionLocalsForPreview(
    crossinline content: @Composable () -> Unit
)

@Composable
@Deprecated(
    "Replaced with ProvideCompositionLocalsForPreview",
    ReplaceWith("ProvideCompositionLocalsForPreview(isDark, content)"),
    level = DeprecationLevel.ERROR,
)
inline fun ProvideFoundationCompositionLocalsForPreview(
    darkMode: DarkMode = DarkMode.AUTO,
    crossinline content: @Composable () -> Unit,
) = ProvideCompositionLocalsForPreview(darkMode, content)

@TestOnly
@Composable
fun ProvideFoundationCompositionLocalsForTest(
    darkMode: DarkMode = DarkMode.LIGHT,
    content: @Composable () -> Unit,
) {
    ProvideCompositionLocalsForPreview(
        darkMode,
    ) {
        content()
    }
}
