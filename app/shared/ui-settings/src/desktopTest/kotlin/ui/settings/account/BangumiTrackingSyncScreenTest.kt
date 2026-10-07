/*
 * Copyright (C) 2026 Wynime contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 * https://github.com/wynime-app/Wynime/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.him188.ani.app.data.repository.subject.BangumiTrackingAccount
import me.him188.ani.app.data.repository.subject.BangumiTrackingConflictPolicy
import me.him188.ani.app.data.repository.subject.BangumiTrackingSyncResult
import me.him188.ani.app.data.repository.subject.BangumiTrackingSyncSettings
import me.him188.ani.app.data.repository.subject.BangumiTrackingSyncSummary
import me.him188.ani.app.data.repository.subject.BangumiSyncOperation
import me.him188.ani.app.data.repository.subject.BangumiSyncPhase
import me.him188.ani.app.data.repository.subject.BangumiSyncProgress
import me.him188.ani.app.data.repository.subject.BangumiSyncUiState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BangumiTrackingSyncScreenTest {
    private class Callbacks {
        var loginClicks = 0
        var syncClicks = 0
        val autoSyncValues = mutableListOf<Boolean>()
        val showResultValues = mutableListOf<Boolean>()
    }

    private fun AniComposeUiTest.render(
        connection: BangumiTrackingConnectionUiState,
        callbacks: Callbacks,
        result: BangumiTrackingSyncResult? = null,
        syncState: BangumiSyncUiState = BangumiSyncUiState.Idle,
        isSyncing: Boolean = false,
    ) {
        setContent {
            ProvideCompositionLocalsForPreview {
                BangumiTrackingSyncContent(
                    connection = connection,
                    settings = BangumiTrackingSyncSettings(),
                    isSyncing = isSyncing,
                    syncState = syncState,
                    isTesting = false,
                    result = result,
                    error = null,
                    summary = BangumiTrackingSyncSummary(
                        lastSuccessfulSyncAt = null,
                        localCount = 2,
                        remoteCount = 3,
                    ),
                    onNavigateToLogin = { callbacks.loginClicks++ },
                    onTestConnection = {},
                    onSyncNow = { callbacks.syncClicks++ },
                    onSetAutoSync = { callbacks.autoSyncValues += it },
                    onSetShowResult = { callbacks.showResultValues += it },
                    onSetConflictPolicy = {},
                )
            }
        }
    }

    @Test
    fun `running sync shows determinate progress and disables repeat click`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(
            BangumiTrackingConnectionUiState.Connected(BangumiTrackingAccount(1, "user")),
            callbacks,
            syncState = BangumiSyncUiState.Running(
                BangumiSyncProgress(
                    operation = BangumiSyncOperation.COLLECTION_REFRESH,
                    phase = BangumiSyncPhase.FETCHING_EPISODES,
                    current = 37,
                    total = 128,
                ),
            ),
        )

        onNodeWithTag("bangumi-tracking-sync-now").assertIsNotEnabled()
        onNodeWithTag("bangumi-tracking-sync-progress").assertIsDisplayed()
        onNodeWithTag("bangumi-tracking-sync-progress-count").assertTextContains("37 / 128")
    }

    @Test
    fun `syncing fallback keeps progress panel visible before first coordinator update`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(
            BangumiTrackingConnectionUiState.Connected(BangumiTrackingAccount(1, "user")),
            callbacks,
            isSyncing = true,
        )

        onNodeWithTag("bangumi-tracking-sync-progress").assertIsDisplayed()
        onNodeWithTag("bangumi-tracking-sync-progress-count").assertTextContains("0 / …")
    }

    @Test
    fun `not connected state disables sync actions and offers login`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(BangumiTrackingConnectionUiState.NotConnected, callbacks)

        onNodeWithTag("bangumi-tracking-auto-sync").assertIsNotEnabled()
        onNodeWithTag("bangumi-tracking-sync-now").assertIsNotEnabled()
        onNodeWithTag("bangumi-tracking-test-connection").performClick()
        waitForIdle()

        assertEquals(1, callbacks.loginClicks)
    }

    @Test
    fun `connected state exposes account and sync controls`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(
            BangumiTrackingConnectionUiState.Connected(BangumiTrackingAccount(1060673, "william")),
            callbacks,
            result = BangumiTrackingSyncResult(1, 2, 3, 4, 1),
        )

        onNodeWithTag("bangumi-tracking-account").assertIsDisplayed()
        onNodeWithTag("bangumi-tracking-auto-sync").performClick()
        onNodeWithTag("bangumi-tracking-sync-now").performClick()
        // The result is rendered in the page's lower summary section and may be
        // below the initial viewport in the desktop test harness.
        onNodeWithTag("bangumi-tracking-sync-result").assertTextContains("同步", substring = true)
            .assertTextContains("刪除收藏", substring = true)
        waitForIdle()

        assertEquals(listOf(true), callbacks.autoSyncValues)
        assertEquals(1, callbacks.syncClicks)
    }

    @Test
    fun `connected state renders all conflict policy choices`() = runAniComposeUiTest {
        val callbacks = Callbacks()
        render(
            BangumiTrackingConnectionUiState.Connected(BangumiTrackingAccount(1, "user")),
            callbacks,
        )

        onNodeWithTag("bangumi-tracking-conflict-policy").assertIsDisplayed().performClick()
        onNodeWithText("以本機為準", substring = true).assertIsDisplayed()
        onNodeWithText("以 Bangumi 為準", substring = true).assertIsDisplayed()
        onNodeWithText("保留最近更新", substring = true).assertIsDisplayed()
        // Keep the enum reference in this test so a future policy addition must update the UI.
        assertEquals(3, BangumiTrackingConflictPolicy.entries.size)
    }
}
