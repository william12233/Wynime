package com.wynime.app.ui.subject.relations

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import com.wynime.app.data.models.preference.DarkMode
import com.wynime.app.data.models.subject.SubjectRelationGraph
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(TestOnly::class, ExperimentalTestApi::class)
class SubjectRelationGraphScreenshotTest {
    private val outDir: File =
        File(System.getProperty("wynime.screenshot.out") ?: "build/screenshots").also { it.mkdirs() }
    private val originalLocale = Locale.getDefault()

    @BeforeTest
    fun setLocale() = Locale.setDefault(Locale.SIMPLIFIED_CHINESE)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(originalLocale)

    private fun capture(graph: SubjectRelationGraph, widthDp: Int, heightDp: Int, name: String, isDark: Boolean = false) {

        val density = Density(2f)
        runSkikoComposeUiTest(Size(widthDp * density.density, heightDp * density.density), density = density) {
            setContent {
                ProvideCompositionLocalsForPreview(if (isDark) DarkMode.DARK else DarkMode.LIGHT) {
                    CompositionLocalProvider(LocalDensity provides density) {
                        SubjectRelationGraphScreen(
                            SubjectRelationGraphUiState(graph, error = null),
                            onRetry = {},
                            onClickSubject = {},
                        )
                    }
                }
            }
            waitForIdle()
            val png = Image.makeFromBitmap(captureToImage().asSkiaBitmap())
                .encodeToData(EncodedImageFormat.PNG)
                ?.bytes
                ?: error("Failed to encode screenshot $name")
            File(outDir, "$name.png").writeBytes(png)
        }
    }

    @Test
    fun compactReZero() = capture(TestSubjectRelationGraphs.ReZero, 390, 1900, "relation-graph-compact-rezero")

    @Test
    fun compactFromOva() =
        capture(TestSubjectRelationGraphs.ReZeroFromOva, 390, 900, "relation-graph-compact-rezero-from-ova")

    @Test
    fun compactKimetsuDark() =
        capture(TestSubjectRelationGraphs.Kimetsu, 390, 1900, "relation-graph-compact-kimetsu-dark", isDark = true)

    @Test
    fun compactRailgun() = capture(TestSubjectRelationGraphs.Railgun, 390, 1000, "relation-graph-compact-railgun")

    @Test
    fun wideReZeroDark() =
        capture(TestSubjectRelationGraphs.ReZero, 1280, 800, "relation-graph-wide-rezero-dark", isDark = true)

    @Test
    fun wideKimetsuDark() =
        capture(TestSubjectRelationGraphs.Kimetsu, 1280, 840, "relation-graph-wide-kimetsu-dark", isDark = true)

    @Test
    fun wideKimetsuLight() = capture(TestSubjectRelationGraphs.Kimetsu, 1280, 840, "relation-graph-wide-kimetsu-light")
}
