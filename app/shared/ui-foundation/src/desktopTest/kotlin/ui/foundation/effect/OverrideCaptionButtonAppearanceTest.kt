package ui.foundation.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.wynime.app.platform.window.LocalTitleBarThemeController
import com.wynime.app.platform.window.TitleBarThemeController
import com.wynime.app.ui.foundation.effects.OverrideCaptionButtonAppearance
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OverrideCaptionButtonAppearanceTest {

    @Test
    fun `test override caption button appearance`() = runWynimeComposeUiTest {
        val titleBarController = TitleBarThemeController()
        val contentDarModeController = mutableStateOf<Boolean?>(null)
        val appDarkMode = mutableStateOf(false)
        setContent {

            CompositionLocalProvider(
                LocalTitleBarThemeController provides titleBarController,
            ) {
                OverrideCaptionButtonAppearance(appDarkMode.value)
                TestContent(contentDarModeController)
            }
        }
        waitForIdle()

        runOnUiThread {
            assertEquals(false, titleBarController.isDark)
        }

        runOnUiThread {
            contentDarModeController.value = true
        }
        waitForIdle()
        runOnUiThread { assertEquals(true, titleBarController.isDark) }

        runOnUiThread {
            contentDarModeController.value = null
        }
        waitForIdle()
        runOnUiThread { assertEquals(false, titleBarController.isDark) }

        runOnUiThread {
            contentDarModeController.value = true
        }
        waitForIdle()
        runOnUiThread { assertEquals(true, titleBarController.isDark) }

        runOnUiThread {
            appDarkMode.value = true
        }
        waitForIdle()
        runOnUiThread { assertEquals(true, titleBarController.isDark) }

        runOnUiThread {
            contentDarModeController.value = null
        }
        waitForIdle()
        runOnUiThread {
            assertEquals(true, titleBarController.isDark)
        }

        runOnUiThread {
            contentDarModeController.value = false
        }
        waitForIdle()
        runOnUiThread { assertEquals(false, titleBarController.isDark) }

        runOnUiThread {
            contentDarModeController.value = null
        }
        waitForIdle()
        runOnUiThread { assertEquals(true, titleBarController.isDark) }

        runOnUiThread {
            appDarkMode.value = false
        }
        waitForIdle()
        runOnUiThread { assertEquals(false, titleBarController.isDark) }
    }

    @Composable
    private fun TestContent(darkMode: State<Boolean?>) {
        when (val currentDark = darkMode.value) {
            null -> {}
            else -> {
                OverrideCaptionButtonAppearance(currentDark)
            }
        }
    }
}
