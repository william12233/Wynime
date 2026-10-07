/*
 * Copyright (C) 2026 Wynime contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 * https://github.com/william12233/Wynime/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.repository.subject.BangumiTrackingAccount
import me.him188.ani.app.data.repository.subject.BangumiTrackingAutoSyncEvent
import me.him188.ani.app.data.repository.subject.BangumiTrackingConnectionError
import me.him188.ani.app.data.repository.subject.BangumiTrackingConnectionResult
import me.him188.ani.app.data.repository.subject.BangumiTrackingConflictPolicy
import me.him188.ani.app.data.repository.subject.BangumiTrackingSyncRepository
import me.him188.ani.app.data.repository.subject.BangumiTrackingSyncResult
import me.him188.ani.app.data.repository.subject.BangumiTrackingSyncSettings
import me.him188.ani.app.data.repository.subject.BangumiTrackingSyncSettingsStore
import me.him188.ani.app.data.repository.subject.BangumiTrackingSyncSummary
import me.him188.ani.app.data.repository.subject.BangumiFullSyncSummary
import me.him188.ani.app.data.repository.subject.BangumiSyncCoordinator
import me.him188.ani.app.data.repository.subject.BangumiSyncOperation
import me.him188.ani.app.data.repository.subject.BangumiSyncPhase
import me.him188.ani.app.data.repository.subject.BangumiSyncProgress
import me.him188.ani.app.data.repository.subject.BangumiSyncUiState
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.subject.classifyBangumiTrackingError
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_auto_sync
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_auto_sync_description
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_auto_success
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_auto_failed
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_remote_delete_unsupported
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_bangumi_account
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_bangumi_first
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_bangumi_first_description
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_complete
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_connected
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_connection_success
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_conflict_when
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_description
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_latest_wins
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_latest_wins_description
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_last_success
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_local_count
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_local_first
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_local_first_description
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_login
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_login_expired
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_never
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_network_error
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_not_connected
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_now
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_preferences
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_progress_applying
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_progress_collections
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_progress_connecting
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_progress_episodes
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_progress_failed
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_progress_merging
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_progress_partial
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_progress_reloading
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_running
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_rate_limited
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_show_result
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_show_result_description
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_test_connection
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_title
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_unknown_error
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_remote_count
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_failed
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_full_summary
import me.him188.ani.app.ui.lang.settings_account_tracking_sync_full_summary_partial
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SingleSelectionElement
import me.him188.ani.app.ui.settings.framework.components.SingleSelectionItem
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.components.TextButtonItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Instant

@Stable
sealed interface BangumiTrackingConnectionUiState {
    data object Checking : BangumiTrackingConnectionUiState
    data object NotConnected : BangumiTrackingConnectionUiState
    data class Connected(val account: BangumiTrackingAccount) : BangumiTrackingConnectionUiState
    data class Failed(val error: BangumiTrackingConnectionError) : BangumiTrackingConnectionUiState
}

@Stable
class BangumiTrackingSyncViewModel : AbstractViewModel(), KoinComponent {
    private val repository: BangumiTrackingSyncRepository by inject()
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val syncCoordinator: BangumiSyncCoordinator by inject()
    private val settingsStore: BangumiTrackingSyncSettingsStore by inject()

    private val _connection = MutableStateFlow<BangumiTrackingConnectionUiState>(
        BangumiTrackingConnectionUiState.Checking,
    )
    val connection: StateFlow<BangumiTrackingConnectionUiState> = _connection.asStateFlow()
    val settings: StateFlow<BangumiTrackingSyncSettings> = settingsStore.flow.stateIn(
        backgroundScope,
        SharingStarted.WhileSubscribed(5_000),
        BangumiTrackingSyncSettings(),
    )
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    // Observe the coordinator directly so the tracking phase and the following full collection
    // phase remain visible as one continuous operation.
    val syncState: StateFlow<BangumiSyncUiState> = syncCoordinator.state
    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()
    private val _lastResult = MutableStateFlow<BangumiTrackingSyncResult?>(null)
    val lastResult: StateFlow<BangumiTrackingSyncResult?> = _lastResult.asStateFlow()
    private val _lastError = MutableStateFlow<BangumiTrackingConnectionError?>(null)
    val lastError: StateFlow<BangumiTrackingConnectionError?> = _lastError.asStateFlow()
    private val _summary = MutableStateFlow<BangumiTrackingSyncSummary?>(null)
    val summary: StateFlow<BangumiTrackingSyncSummary?> = _summary.asStateFlow()
    private val _fullSyncSummary = MutableStateFlow<BangumiFullSyncSummary?>(null)
    val fullSyncSummary: StateFlow<BangumiFullSyncSummary?> = _fullSyncSummary.asStateFlow()
    private val _autoResult = MutableStateFlow<BangumiTrackingAutoSyncEvent?>(null)
    val autoResult: StateFlow<BangumiTrackingAutoSyncEvent?> = _autoResult.asStateFlow()

    init {
        backgroundScope.launch {
            repository.autoSyncEvents.collect { event ->
                if (settingsStore.flow.first().showSyncResult) {
                    _autoResult.value = event
                }
                _summary.value = repository.summary()
            }
        }
        backgroundScope.launch {
            loadStoredSyncResult()
        }
        backgroundScope.launch {
            syncCoordinator.state.collect { state ->
                if (state is BangumiSyncUiState.Completed ||
                    state is BangumiSyncUiState.PartialFailure ||
                    state is BangumiSyncUiState.Failed
                ) {
                    // The full sync deliberately outlives this screen. Reload its terminal
                    // result when returning from another settings page or after the screen
                    // owner recreated the ViewModel.
                    loadStoredSyncResult()
                }
            }
        }
        testConnection()
    }

    private suspend fun loadStoredSyncResult() {
        _summary.value = repository.summary()
        _fullSyncSummary.value = subjectCollectionRepository.getBangumiFullSyncSummary()
    }

    fun testConnection() {
        if (_isTestingConnection.value) return
        backgroundScope.launch {
            _isTestingConnection.value = true
            _connection.value = BangumiTrackingConnectionUiState.Checking
            _lastError.value = null
            try {
                when (val result = repository.testConnection()) {
                    is BangumiTrackingConnectionResult.Connected -> {
                        _connection.value = BangumiTrackingConnectionUiState.Connected(result.account)
                        _summary.value = repository.summary()
                        repository.retryPendingChanges(result.account)
                    }

                    BangumiTrackingConnectionResult.NotConnected -> {
                        _connection.value = BangumiTrackingConnectionUiState.NotConnected
                        _summary.value = null
                    }

                    is BangumiTrackingConnectionResult.Failed -> {
                        _connection.value = BangumiTrackingConnectionUiState.Failed(result.error)
                        _lastError.value = result.error
                    }
                }
            } finally {
                _isTestingConnection.value = false
            }
        }
    }

    fun syncNow() {
        if (_isSyncing.value || _connection.value !is BangumiTrackingConnectionUiState.Connected) return
        backgroundScope.launch {
            _isSyncing.value = true
            _lastError.value = null
            _fullSyncSummary.value = null
            try {
                val trackingResult = repository.syncNow()
                _lastResult.value = trackingResult
                // Tracking sync resolves collection conflicts first. The full collection pass then
                // fetches all five tabs and every episode snapshot, including watched state.
                try {
                    subjectCollectionRepository.performBangumiFullSync()
                } finally {
                    val fullSummary = subjectCollectionRepository.getBangumiFullSyncSummary()
                    _fullSyncSummary.value = fullSummary
                    if (fullSummary != null) {
                        _lastResult.value = trackingResult.copy(
                            fetchedEpisodeSubjectCount = fullSummary.savedSubjectCount,
                            episodeCount = fullSummary.episodeCount,
                            episodeUpdated = fullSummary.episodeSnapshotCount,
                            failedSubjectIds = fullSummary.failedSubjectIds,
                            elapsedMillis = trackingResult.elapsedMillis + fullSummary.elapsedMillis,
                        )
                    }
                }
                _summary.value = repository.summary()
            } catch (e: Throwable) {
                _lastError.value = classifyBangumiTrackingError(e)
                if (_lastError.value == BangumiTrackingConnectionError.AUTHORIZATION) {
                    _connection.value = BangumiTrackingConnectionUiState.Failed(BangumiTrackingConnectionError.AUTHORIZATION)
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun setAutoSync(value: Boolean) = backgroundScope.launch { settingsStore.setAutoSyncTracking(value) }
    fun setShowResult(value: Boolean) = backgroundScope.launch { settingsStore.setShowSyncResult(value) }
    fun setConflictPolicy(value: BangumiTrackingConflictPolicy) =
        backgroundScope.launch { settingsStore.setConflictPolicy(value) }
}

@Composable
fun BangumiTrackingSyncScreen(
    onNavigateToLogin: () -> Unit,
    vm: BangumiTrackingSyncViewModel = viewModel { BangumiTrackingSyncViewModel() },
    modifier: Modifier = Modifier,
) {
    val connection by vm.connection.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isSyncing by vm.isSyncing.collectAsStateWithLifecycle()
    val syncState by vm.syncState.collectAsStateWithLifecycle()
    val fullSyncSummary by vm.fullSyncSummary.collectAsStateWithLifecycle()
    val isTesting by vm.isTestingConnection.collectAsStateWithLifecycle()
    val result by vm.lastResult.collectAsStateWithLifecycle()
    val error by vm.lastError.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val autoResult by vm.autoResult.collectAsStateWithLifecycle()

    BangumiTrackingSyncContent(
        connection = connection,
        settings = settings,
        isSyncing = isSyncing,
        syncState = syncState,
        fullSyncSummary = fullSyncSummary,
        isTesting = isTesting,
        result = result,
        autoResult = autoResult,
        error = error,
        summary = summary,
        onNavigateToLogin = onNavigateToLogin,
        onTestConnection = vm::testConnection,
        onSyncNow = vm::syncNow,
        onSetAutoSync = vm::setAutoSync,
        onSetShowResult = vm::setShowResult,
        onSetConflictPolicy = vm::setConflictPolicy,
        modifier = modifier,
    )
}

@Composable
internal fun BangumiTrackingSyncContent(
    connection: BangumiTrackingConnectionUiState,
    settings: BangumiTrackingSyncSettings,
    isSyncing: Boolean,
    syncState: BangumiSyncUiState = BangumiSyncUiState.Idle,
    fullSyncSummary: BangumiFullSyncSummary? = null,
    isTesting: Boolean,
    result: BangumiTrackingSyncResult?,
    error: BangumiTrackingConnectionError?,
    summary: BangumiTrackingSyncSummary?,
    onNavigateToLogin: () -> Unit,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
    onSetAutoSync: (Boolean) -> Unit,
    onSetShowResult: (Boolean) -> Unit,
    onSetConflictPolicy: (BangumiTrackingConflictPolicy) -> Unit,
    autoResult: BangumiTrackingAutoSyncEvent? = null,
    modifier: Modifier = Modifier,
) = SettingsTab(modifier) {
    val connected = connection as? BangumiTrackingConnectionUiState.Connected
    val syncRunning = isSyncing || syncState is BangumiSyncUiState.Running
    Group(
        title = { Text(stringResource(Lang.settings_account_tracking_sync_bangumi_account)) },
        description = { Text(stringResource(Lang.settings_account_tracking_sync_description)) },
    ) {
        TextItem(
            title = { Text(stringResource(Lang.settings_account_tracking_sync_bangumi_account)) },
            description = {
                Text(
                    connected?.let { "${it.account.username} · ${it.account.id}" }
                        ?: stringResource(Lang.settings_account_tracking_sync_not_connected),
                )
            },
            onClick = if (connected == null) onNavigateToLogin else null,
            modifier = Modifier.testTag("bangumi-tracking-account"),
        )
        TextButtonItem(
            onClick = if (connected == null) onNavigateToLogin else onTestConnection,
            enabled = !isTesting,
            title = {
                if (isTesting) CircularProgressIndicator() else Text(
                    stringResource(
                        if (connected == null) Lang.settings_account_tracking_sync_login
                        else Lang.settings_account_tracking_sync_test_connection,
                    ),
                )
            },
            modifier = Modifier.testTag("bangumi-tracking-test-connection"),
        )
        connected?.let {
            TextItem(
                title = { Text(stringResource(Lang.settings_account_tracking_sync_connected, it.account.username)) },
                description = {
                    Text(
                        when (error) {
                            null -> stringResource(Lang.settings_account_tracking_sync_connection_success)
                            BangumiTrackingConnectionError.AUTHORIZATION -> stringResource(Lang.settings_account_tracking_sync_login_expired)
                            BangumiTrackingConnectionError.NETWORK -> stringResource(Lang.settings_account_tracking_sync_network_error)
                            else -> stringResource(Lang.settings_account_tracking_sync_unknown_error)
                        },
                    )
                },
            )
        }
        if (connection is BangumiTrackingConnectionUiState.Failed) {
            TextItem(
                title = { Text(connectionErrorText(connection.error)) },
                modifier = Modifier.testTag("bangumi-tracking-connection-error"),
            )
        }
    }

    Group({ Text(stringResource(Lang.settings_account_tracking_sync_auto_sync)) }) {
        SwitchItem(
            checked = settings.autoSyncTracking,
            onCheckedChange = onSetAutoSync,
            enabled = connected != null,
            title = { Text(stringResource(Lang.settings_account_tracking_sync_auto_sync)) },
            description = { Text(stringResource(Lang.settings_account_tracking_sync_auto_sync_description)) },
            modifier = Modifier.testTag("bangumi-tracking-auto-sync"),
        )
        TextButtonItem(
            onClick = onSyncNow,
            enabled = connected != null && !syncRunning,
            title = {
                if (syncRunning) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(Lang.settings_account_tracking_sync_now))
                }
            },
            modifier = Modifier.testTag("bangumi-tracking-sync-now"),
        )
        if (syncRunning) {
            BangumiTrackingSyncProgressPanel(syncState)
        }
        when (val terminalState = syncState) {
            is BangumiSyncUiState.PartialFailure -> {
                TextItem(
                    title = {
                        Text(
                            stringResource(
                                Lang.settings_account_tracking_sync_progress_partial,
                                terminalState.progress.current,
                                terminalState.progress.total ?: terminalState.progress.current,
                                terminalState.progress.failedCount,
                            ),
                        )
                    },
                    onClick = onSyncNow,
                    onClickEnabled = !syncRunning,
                    modifier = Modifier.testTag("bangumi-tracking-sync-partial-failure"),
                )
            }

            is BangumiSyncUiState.Failed -> {
                TextItem(
                    title = {
                        Text(
                            stringResource(
                                Lang.settings_account_tracking_sync_progress_failed,
                                terminalState.error,
                            ),
                        )
                    },
                    onClick = onSyncNow,
                    onClickEnabled = !syncRunning,
                    modifier = Modifier.testTag("bangumi-tracking-sync-failure"),
                )
            }

            else -> Unit
        }
    }

    Group({ Text(stringResource(Lang.settings_account_tracking_sync_preferences)) }) {
        SingleSelectionItem(
            items = BangumiTrackingConflictPolicy.entries.map { SingleSelectionElement(it, connected != null) },
            selected = settings.conflictPolicy.ordinal,
            key = { it },
            description = { policy -> policy?.let { policyDescription(it) } ?: Text("") },
            listItem = { policy ->
                Column {
                    Text(policyTitle(policy))
                    Text(policyDescription(policy))
                }
            },
            onConfirm = { policy -> if (policy != null) onSetConflictPolicy(policy) },
            onSelectItem = { connected != null },
            title = { Text(stringResource(Lang.settings_account_tracking_sync_conflict_when)) },
            modifier = Modifier.testTag("bangumi-tracking-conflict-policy"),
        )
        SwitchItem(
            checked = settings.showSyncResult,
            onCheckedChange = onSetShowResult,
            title = { Text(stringResource(Lang.settings_account_tracking_sync_show_result)) },
            description = { Text(stringResource(Lang.settings_account_tracking_sync_show_result_description)) },
            modifier = Modifier.testTag("bangumi-tracking-show-result"),
        )
    }

    Group({ Text(stringResource(Lang.settings_account_tracking_sync_last_success)) }) {
        TextItem(
            title = {
                Text(summary?.lastSuccessfulSyncAt?.let(::formatTrackingSyncTime)
                    ?: stringResource(Lang.settings_account_tracking_sync_never))
            },
            description = {
                Text(
                    "${stringResource(Lang.settings_account_tracking_sync_local_count, summary?.localCount ?: 0)} · " +
                        stringResource(Lang.settings_account_tracking_sync_remote_count, summary?.remoteCount ?: 0),
                )
            },
        )
        result?.let {
            TextItem(
                title = {
                    Text(
                        if (it.failedSubjectIds.isEmpty()) {
                            stringResource(
                                Lang.settings_account_tracking_sync_complete,
                                it.localUpdated,
                                it.bangumiUpdated,
                                it.unchanged,
                                it.conflictsResolved,
                                it.remoteDeleteUnsupported,
                            )
                        } else {
                            stringResource(
                                Lang.settings_account_tracking_sync_progress_partial,
                                it.fetchedCollectionCount,
                                it.expectedCollectionCount ?: it.fetchedCollectionCount,
                                it.failedSubjectIds.size,
                            )
                        },
                    )
                },
                modifier = Modifier.testTag("bangumi-tracking-sync-result"),
            )
        }
        fullSyncSummary?.let { summary ->
            val expected = summary.expectedSubjectCount ?: summary.savedSubjectCount
            TextItem(
                title = {
                    Text(
                        if (summary.failedSubjectIds.isEmpty()) {
                            stringResource(
                                Lang.settings_account_tracking_sync_full_summary,
                                summary.savedSubjectCount,
                                expected,
                                summary.episodeCount,
                                summary.watchedEpisodeCount,
                                summary.elapsedMillis,
                            )
                        } else {
                            stringResource(
                                Lang.settings_account_tracking_sync_full_summary_partial,
                                summary.savedSubjectCount,
                                expected,
                                summary.episodeCount,
                                summary.watchedEpisodeCount,
                                summary.failedSubjectIds.size,
                                summary.elapsedMillis,
                            )
                        },
                    )
                },
                modifier = Modifier.testTag("bangumi-tracking-sync-full-summary"),
            )
        }
        autoResult?.let { event ->
            TextItem(
                title = {
                    Text(
                        when (event) {
                            is BangumiTrackingAutoSyncEvent.Succeeded ->
                                stringResource(Lang.settings_account_tracking_sync_auto_success)

                            is BangumiTrackingAutoSyncEvent.Failed ->
                                stringResource(
                                    Lang.settings_account_tracking_sync_auto_failed,
                                    connectionErrorText(event.error),
                                )

                            is BangumiTrackingAutoSyncEvent.RemoteDeleteUnsupported ->
                                stringResource(Lang.settings_account_tracking_sync_remote_delete_unsupported)
                        },
                    )
                },
                modifier = Modifier.testTag("bangumi-tracking-auto-result"),
            )
        }
        error?.let {
            TextItem(
                title = { Text(stringResource(Lang.settings_account_tracking_sync_failed, connectionErrorText(it))) },
                modifier = Modifier.testTag("bangumi-tracking-sync-error"),
            )
        }
    }
}

@Composable
private fun BangumiTrackingSyncProgressPanel(state: BangumiSyncUiState) {
    val progress = when (state) {
        is BangumiSyncUiState.Running -> state.progress
        // The view model keeps the panel alive while the second half of the full sync is being
        // claimed. Keep a visible connecting state instead of replacing it with a bare spinner.
        else -> BangumiSyncProgress(
            operation = BangumiSyncOperation.TRACKING,
            phase = BangumiSyncPhase.CONNECTING,
            current = 0,
            total = null,
        )
    }
    val total = progress.total
    val phaseText = when (progress.phase) {
        BangumiSyncPhase.CONNECTING -> stringResource(Lang.settings_account_tracking_sync_progress_connecting)
        BangumiSyncPhase.FETCHING_COLLECTIONS -> if (total == null) {
            stringResource(Lang.settings_account_tracking_sync_running)
        } else {
            stringResource(
                Lang.settings_account_tracking_sync_progress_collections,
                progress.current,
                total,
            )
        }

        BangumiSyncPhase.FETCHING_EPISODES -> if (total == null) {
            stringResource(Lang.settings_account_tracking_sync_running)
        } else {
            stringResource(
                Lang.settings_account_tracking_sync_progress_episodes,
                progress.current,
                total,
            )
        }

        BangumiSyncPhase.MERGING -> stringResource(
            Lang.settings_account_tracking_sync_progress_merging,
            progress.current,
            total ?: progress.current,
        )

        BangumiSyncPhase.APPLYING_REMOTE,
        BangumiSyncPhase.APPLYING_LOCAL,
        -> stringResource(
            Lang.settings_account_tracking_sync_progress_applying,
            progress.current,
            total ?: progress.current,
        )

        BangumiSyncPhase.RELOADING -> stringResource(Lang.settings_account_tracking_sync_progress_reloading)
        BangumiSyncPhase.COMPLETED,
        BangumiSyncPhase.PARTIAL_FAILURE,
        BangumiSyncPhase.FAILED,
        -> stringResource(Lang.settings_account_tracking_sync_running)
    }
    Column(Modifier.fillMaxWidth().testTag("bangumi-tracking-sync-progress")) {
        Text(phaseText)
        if (total == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("bangumi-tracking-sync-progress-bar"))
        } else {
            val fraction = if (total == 0) 0f else (progress.current.toFloat() / total).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().testTag("bangumi-tracking-sync-progress-bar"),
            )
        }
        Text(
            text = "${progress.current} / ${total ?: "…"}",
            modifier = Modifier.testTag("bangumi-tracking-sync-progress-count"),
        )
    }
}

@Composable
private fun connectionErrorText(error: BangumiTrackingConnectionError): String = when (error) {
    BangumiTrackingConnectionError.AUTHORIZATION -> stringResource(Lang.settings_account_tracking_sync_login_expired)
    BangumiTrackingConnectionError.NETWORK -> stringResource(Lang.settings_account_tracking_sync_network_error)
    BangumiTrackingConnectionError.RATE_LIMITED -> stringResource(Lang.settings_account_tracking_sync_rate_limited)
    BangumiTrackingConnectionError.UNKNOWN -> stringResource(Lang.settings_account_tracking_sync_unknown_error)
}

@Composable
private fun policyTitle(policy: BangumiTrackingConflictPolicy): String = when (policy) {
    BangumiTrackingConflictPolicy.LOCAL_FIRST -> stringResource(Lang.settings_account_tracking_sync_local_first)
    BangumiTrackingConflictPolicy.BANGUMI_FIRST -> stringResource(Lang.settings_account_tracking_sync_bangumi_first)
    BangumiTrackingConflictPolicy.LATEST_WINS -> stringResource(Lang.settings_account_tracking_sync_latest_wins)
}

@Composable
private fun policyDescription(policy: BangumiTrackingConflictPolicy): String = when (policy) {
    BangumiTrackingConflictPolicy.LOCAL_FIRST -> stringResource(Lang.settings_account_tracking_sync_local_first_description)
    BangumiTrackingConflictPolicy.BANGUMI_FIRST -> stringResource(Lang.settings_account_tracking_sync_bangumi_first_description)
    BangumiTrackingConflictPolicy.LATEST_WINS -> stringResource(Lang.settings_account_tracking_sync_latest_wins_description)
}

private fun formatTrackingSyncTime(epochMillis: Long): String {
    val value = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .toString()
        .replace('T', ' ')
    return value.take(16)
}
