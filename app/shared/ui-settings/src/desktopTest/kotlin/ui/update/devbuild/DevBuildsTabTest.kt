package com.wynime.app.ui.update.devbuild

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.framework.WynimeComposeUiTest
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.SystemPaths
import com.wynime.utils.io.createTempDirectory
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.io.resolve
import kotlin.test.Test
import kotlin.test.assertEquals

class DevBuildsTabTest {
    private val spec = DevBuildPackageSpec(listOf("wynime-android-arm64-v8a-release"), DevBuildPackageKind.ANDROID_APK)

    private data class Fixture(val saveDir: SystemPath, val installer: FakeInstaller)

    private fun withTab(
        client: HttpClient,
        block: WynimeComposeUiTest.(Fixture) -> Unit,
    ) = runWynimeComposeUiTest {
        val dir = SystemPaths.createTempDirectory("dev-builds-tab-test")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val fixture = Fixture(dir.resolve("dev-builds"), FakeInstaller())
            val state = DevBuildsState(
                api = GitHubDevBuildApi(client),
                spec = spec,
                installer = fixture.installer,
                saveDir = fixture.saveDir,
                getToken = { "token" },
                currentVersionName = "4.12.0-main-bbbbbbbb",
                backgroundScope = scope,
                installDispatcher = Dispatchers.Default,
            )
            setContent {
                ProvideCompositionLocalsForPreview {
                    DevBuildsTabContent(
                        state = state,
                        token = "token",
                        onTokenChange = {},
                        modifier = Modifier.fillMaxSize(),
                        currentVersion = "4.12.0-main-bbbbbbbb",
                    )
                }
            }
            block(fixture)
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `lists commits and installs the selected one after confirmation`() = withTab(
        fullGitHubMockClient("wynime-android-arm64-v8a-release", zipBytes("Wynime-4.12.0.apk" to byteArrayOf(1))),
    ) { (saveDir, installer) ->

        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(DevBuildsTestTags.COMMIT_PREFIX + SHA_A).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(DevBuildsTestTags.COMMIT_PREFIX + SHA_A).assertIsDisplayed()
        onNodeWithTag(DevBuildsTestTags.INSTALL_BUTTON_PREFIX + SHA_A).assertIsEnabled()
        onNodeWithTag(DevBuildsTestTags.INSTALL_BUTTON_PREFIX + SHA_B).assertIsNotEnabled()

        onNodeWithTag(DevBuildsTestTags.INSTALL_BUTTON_PREFIX + SHA_A).performClick()
        waitForIdle()
        onNodeWithTag(DevBuildsTestTags.CONFIRM_CANCEL_BUTTON).assertIsDisplayed().performClick()
        waitForIdle()
        onNodeWithTag(DevBuildsTestTags.CONFIRM_BUTTON).assertDoesNotExist()
        assertEquals(emptyList(), installer.installed)

        onNodeWithTag(DevBuildsTestTags.INSTALL_BUTTON_PREFIX + SHA_A).performClick()
        waitForIdle()
        onNodeWithTag(DevBuildsTestTags.CONFIRM_BUTTON).assertIsDisplayed().performClick()

        waitUntil(timeoutMillis = 10_000) { installer.installed.isNotEmpty() }
        assertEquals(listOf(saveDir.resolve("wynime-main-aaaaaaaa.apk")), installer.installed)
    }

    @Test
    fun `looks up a pasted commit link and installs it from the result card`() = withTab(
        fullGitHubMockClient("wynime-android-arm64-v8a-release", zipBytes("Wynime-4.12.0.apk" to byteArrayOf(1))),
    ) { (saveDir, installer) ->
        onNodeWithTag(DevBuildsTestTags.LOOKUP_BUTTON).assertIsNotEnabled()
        onNodeWithTag(DevBuildsTestTags.LOOKUP_FIELD).performTextInput("https://github.com/william12233/Wynime/commit/$SHA_A")
        onNodeWithTag(DevBuildsTestTags.LOOKUP_BUTTON).assertIsEnabled().performClick()

        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(DevBuildsTestTags.LOOKUP_RESULT).fetchSemanticsNodes().isNotEmpty()
        }
        val inResult = hasAnyAncestor(hasTestTag(DevBuildsTestTags.LOOKUP_RESULT))
        onNode(hasTestTag(DevBuildsTestTags.COMMIT_PREFIX + SHA_A) and inResult).assertIsDisplayed()
        onNode(hasTestTag(DevBuildsTestTags.INSTALL_BUTTON_PREFIX + SHA_A) and inResult).assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithTag(DevBuildsTestTags.CONFIRM_BUTTON).assertIsDisplayed().performClick()

        waitUntil(timeoutMillis = 10_000) { installer.installed.isNotEmpty() }
        assertEquals(listOf(saveDir.resolve("wynime-main-aaaaaaaa.apk")), installer.installed)
    }

    @Test
    fun `shows an error for unrecognized input and clears it`() = withTab(
        fullGitHubMockClient("wynime-android-arm64-v8a-release", zipBytes()),
    ) {
        onNodeWithTag(DevBuildsTestTags.LOOKUP_FIELD).performTextInput("what is this")
        onNodeWithTag(DevBuildsTestTags.LOOKUP_BUTTON).performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(DevBuildsTestTags.LOOKUP_ERROR).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(DevBuildsTestTags.LOOKUP_CLEAR_BUTTON).performClick()
        waitForIdle()
        onNodeWithTag(DevBuildsTestTags.LOOKUP_ERROR).assertDoesNotExist()
    }

    @Test
    fun `installs a package from a direct link`() = withTab(
        gitHubMockClient { request ->
            when (request.url.host) {
                "example.com" -> respond(byteArrayOf(9), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "1"))
                else -> fullGitHubMockHandler("wynime-android-arm64-v8a-release", zipBytes())(request)
            }
        },
    ) { (saveDir, installer) ->
        onNodeWithTag(DevBuildsTestTags.LOOKUP_FIELD).performTextInput("https://example.com/dl/Wynime-dev.apk")
        onNodeWithTag(DevBuildsTestTags.LOOKUP_BUTTON).performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(DevBuildsTestTags.PACKAGE_ROW).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(DevBuildsTestTags.PACKAGE_INSTALL_BUTTON).assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithTag(DevBuildsTestTags.CONFIRM_BUTTON).assertIsDisplayed().performClick()

        waitUntil(timeoutMillis = 10_000) { installer.installed.isNotEmpty() }
        assertEquals(listOf(saveDir.resolve("Wynime-dev.apk")), installer.installed)
    }
}
