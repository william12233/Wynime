package com.wynime.app.ui.subject.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.io.files.Path
import com.wynime.app.data.models.preference.DarkMode
import com.wynime.app.ui.foundation.DragAndDropContent
import com.wynime.app.ui.foundation.LocalWindowDropHandlerRegistry
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.WindowDropHandlerEffect
import com.wynime.app.ui.foundation.WindowDropHandlerRegistry
import com.wynime.app.ui.foundation.WindowDropHost
import com.wynime.app.ui.foundation.WindowDropHostState
import com.wynime.app.ui.foundation.WindowDropTestTags
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.media_selector_summary_dropped_file
import com.wynime.app.ui.mediaselect.summary.MediaSelectorSourceSummary
import com.wynime.app.ui.mediaselect.summary.MediaSelectorSummary
import com.wynime.app.ui.mediaselect.summary.MediaSelectorSummaryCard
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.util.Locale
import kotlin.test.Test

@OptIn(TestOnly::class, ExperimentalTestApi::class)
class EpisodeVideoDropScreenshotTest {
    private val outDir: File =
        File(System.getProperty("wynime.screenshot.out") ?: "build/screenshots").also { it.mkdirs() }

    private fun capture(
        name: String,
        darkMode: DarkMode,
        width: Int = 1280,
        height: Int = 720,
        content: @Composable () -> Unit,
        body: SkikoComposeUiTest.() -> Unit = {},
    ) {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        try {
            runSkikoComposeUiTest(Size(width.toFloat(), height.toFloat()), density = Density(1f)) {
                setContent {
                    ProvideCompositionLocalsForPreview(darkMode = darkMode) {
                        CompositionLocalProvider(LocalDensity provides Density(1f)) {
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                content()
                            }
                        }
                    }
                }
                waitForIdle()
                body()
                waitForIdle()
                val png = Image.makeFromBitmap(captureToImage().asSkiaBitmap())
                    .encodeToData(EncodedImageFormat.PNG)
                    ?.bytes
                    ?: error("Failed to encode screenshot $name")
                File(outDir, "$name.png").writeBytes(png)
            }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    private fun captureDrop(name: String, darkMode: DarkMode, dragged: String, expectedText: String) {
        val state = WindowDropHostState()
        var registry: WindowDropHandlerRegistry? = null
        capture(
            name, darkMode,
            content = {
                WindowDropHost(emptyList(), Modifier.fillMaxSize(), state) {
                    registry = LocalWindowDropHandlerRegistry.current
                    WindowDropHandlerEffect(rememberEpisodeVideoDropHandler {})
                    PlayerPageBackdrop()
                }
            },
        ) {
            state.onDragStarted(DragAndDropContent.FileList(listOf(Path("/Users/ani/Movies/$dragged"))), registry!!.handlers)
            waitForIdle()
            onNodeWithTag(WindowDropTestTags.OVERLAY).assertIsDisplayed()
            onNodeWithText(expectedText, substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun dropVideoDark() = captureDrop(
        "episode-drop-video-dark", DarkMode.DARK,
        dragged = "[Sakurato] Sousou no Frieren [01][AVC-8bit 1080p AAC][CHS].mp4",
        expectedText = "松手以播放",
    )

    @Test
    fun dropVideoLight() = captureDrop(
        "episode-drop-video-light", DarkMode.LIGHT,
        dragged = "[Sakurato] Sousou no Frieren [01][AVC-8bit 1080p AAC][CHS].mp4",
        expectedText = "松手以播放",
    )

    @Test
    fun dropUnsupportedDark() = captureDrop(
        "episode-drop-unsupported-dark", DarkMode.DARK,
        dragged = "字幕.ass",
        expectedText = "视频文件",
    )

    @Test
    fun dropUnsupportedLight() = captureDrop(
        "episode-drop-unsupported-light", DarkMode.LIGHT,
        dragged = "字幕.ass",
        expectedText = "视频文件",
    )

    private fun captureSummary(name: String, darkMode: DarkMode) = capture(name, darkMode, width = 420, height = 200, content = {
        Box(Modifier.padding(16.dp)) {
            MediaSelectorSummaryCard(
                MediaSelectorSummary.Selected(
                    source = MediaSelectorSourceSummary(
                        sourceName = stringResource(Lang.media_selector_summary_dropped_file),
                        sourceIconUrl = "",
                    ),
                    mediaTitle = "[Sakurato] Sousou no Frieren [01][AVC-8bit 1080p AAC][CHS].mp4",
                    isPerfectMatch = false,
                ),
                onClickManualSelect = {},
                Modifier.width(388.dp),
            )
        }
    })

    @Test
    fun summaryDark() = captureSummary("episode-drop-summary-dark", DarkMode.DARK)

    @Test
    fun summaryLight() = captureSummary("episode-drop-summary-light", DarkMode.LIGHT)
}

@Composable
private fun PlayerPageBackdrop() {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth(0.72f).aspectRatio(16f / 9f).background(Color.Black))
    }
}
