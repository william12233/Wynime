package com.wynime.app.ui.settings.account

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.wynime.app.data.repository.subject.BangumiTrackingAccount
import com.wynime.app.data.repository.subject.BangumiCollectionUpstreamStatus
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
                    onSetShowResult = { callbacks.showResultValues += it },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
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
        onNodeWithTag("bangumi-tracking-sync-now").performClick()

        onNodeWithTag("bangumi-tracking-sync-result").assertTextContains("同步", substring = true)
            .assertTextContains("刪除收藏", substring = true)
        waitForIdle()

        assertEquals(1, callbacks.syncClicks)
    }

    @Test
    fun `connected state shows timestamp based conflict resolution`() = runWynimeComposeUiTest {
        val callbacks = Callbacks()
        render(
            BangumiTrackingConnectionUiState.Connected(BangumiTrackingAccount(1, "user")),
            callbacks,
        )

        onNodeWithTag("bangumi-tracking-conflict-policy").assertIsDisplayed()
        onNodeWithText("保留最近更新", substring = true).assertIsDisplayed()
    }

    @Test
    fun `cloud pending status remains visible after local sync`() = runWynimeComposeUiTest {
        val callbacks = Callbacks()
        render(
            BangumiTrackingConnectionUiState.Connected(BangumiTrackingAccount(1, "user")),
            callbacks,
            result = BangumiTrackingSyncResult(
                localUpdated = 0,
                bangumiUpdated = 0,
                unchanged = 1,
                conflictsResolved = 0,
                cloudSyncStatus = BangumiCollectionUpstreamStatus.PENDING,
                cloudSyncPendingCount = 2,
            ),
        )

        onNodeWithTag("bangumi-tracking-cloud-sync-pending").performScrollTo().assertIsDisplayed()
        onNodeWithText("等待 Bangumi", substring = true).assertIsDisplayed()
    }
}
