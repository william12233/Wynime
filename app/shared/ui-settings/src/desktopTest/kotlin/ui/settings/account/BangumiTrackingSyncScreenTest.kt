package com.wynime.app.ui.settings.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wynime.app.data.repository.subject.BangumiTrackingAccount
import com.wynime.app.data.repository.subject.BangumiTrackingConflictPolicy
import com.wynime.app.data.repository.subject.BangumiTrackingSyncResult
import com.wynime.app.data.repository.subject.BangumiTrackingSyncSettings
import com.wynime.app.data.repository.subject.BangumiTrackingSyncSummary
import com.wynime.app.data.repository.subject.BangumiSyncOperation
import com.wynime.app.data.repository.subject.BangumiSyncPhase
import com.wynime.app.data.repository.subject.BangumiSyncProgress
import com.wynime.app.data.repository.subject.BangumiSyncUiState
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.framework.WynimeComposeUiTest
import com.wynime.app.ui.framework.runWynimeComposeUiTest
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BangumiTrackingSyncScreenTest {
    private val originalLocale = Locale.getDefault()

    @BeforeTest
    fun useTestLocale() = Locale.setDefault(Locale.TRADITIONAL_CHINESE)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(originalLocale)

    private class Callbacks {
        var loginClicks = 0
        var syncClicks = 0
        val autoSyncValues = mutableListOf<Boolean>()
        val showResultValues = mutableListOf<Boolean>()
    }

    private fun WynimeComposeUiTest.render(
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
    fun `running sync shows determinate progress and disables repeat click`() = runWynimeComposeUiTest {
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
    fun `syncing fallback keeps progress panel visible before first coordinator update`() = runWynimeComposeUiTest {
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
    fun `not connected state disables sync actions and offers login`() = runWynimeComposeUiTest {
        val callbacks = Callbacks()
        render(BangumiTrackingConnectionUiState.NotConnected, callbacks)

        onNodeWithTag("bangumi-tracking-auto-sync").assertIsNotEnabled()
        onNodeWithTag("bangumi-tracking-sync-now").assertIsNotEnabled()
        onNodeWithTag("bangumi-tracking-test-connection").performClick()
        waitForIdle()

        assertEquals(1, callbacks.loginClicks)
    }

    @Test
    fun `connected state exposes account and sync controls`() = runWynimeComposeUiTest {
        val callbacks = Callbacks()
        render(
            BangumiTrackingConnectionUiState.Connected(BangumiTrackingAccount(1060673, "william")),
            callbacks,
            result = BangumiTrackingSyncResult(1, 2, 3, 4, 1),
        )

        onNodeWithTag("bangumi-tracking-account").assertIsDisplayed()
        onNodeWithTag("bangumi-tracking-auto-sync").performClick()
        onNodeWithTag("bangumi-tracking-sync-now").performClick()

        onNodeWithTag("bangumi-tracking-sync-result").assertTextContains("同步", substring = true)
            .assertTextContains("刪除收藏", substring = true)
        waitForIdle()

        assertEquals(listOf(true), callbacks.autoSyncValues)
        assertEquals(1, callbacks.syncClicks)
    }

    @Test
    fun `connected state renders all conflict policy choices`() = runWynimeComposeUiTest {
        val callbacks = Callbacks()
        render(
            BangumiTrackingConnectionUiState.Connected(BangumiTrackingAccount(1, "user")),
            callbacks,
        )

        onNodeWithTag("bangumi-tracking-conflict-policy").assertIsDisplayed().performClick()
        onNodeWithText("以本機為準", substring = true).assertIsDisplayed()
        onNodeWithText("以 Bangumi 為準", substring = true).assertIsDisplayed()
        onNodeWithText("保留最近更新", substring = true).assertIsDisplayed()

        assertEquals(3, BangumiTrackingConflictPolicy.entries.size)
    }
}
