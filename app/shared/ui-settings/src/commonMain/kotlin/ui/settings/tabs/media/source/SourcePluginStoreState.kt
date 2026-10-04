/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.sourceplugin.SourcePluginIndexEntry
import me.him188.ani.app.domain.sourceplugin.SourcePluginRepositoryCache
import me.him188.ani.app.domain.sourceplugin.SourcePluginRegistry
import me.him188.ani.app.domain.sourceplugin.SourcePluginRepositoryClient

/** State and actions for the first-party executable source-plugin store. */
class SourcePluginStoreState(
    private val repositoryClient: SourcePluginRepositoryClient,
    private val registry: SourcePluginRegistry,
    private val repositoryCache: DataStore<SourcePluginRepositoryCache>,
    private val scope: CoroutineScope,
) {
    val installed = registry.states

    private val _available = MutableStateFlow<List<SourcePluginIndexEntry>>(emptyList())
    val available: StateFlow<List<SourcePluginIndexEntry>> = _available.asStateFlow()

    private val _busyPluginIds = MutableStateFlow<Set<String>>(emptySet())
    val busyPluginIds: StateFlow<Set<String>> = _busyPluginIds.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var refreshJob: Job? = null

    fun refresh(): Job {
        refreshJob?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            _isRefreshing.value = true
            _error.value = null
            try {
                val cached = repositoryCache.data.first()
                cached.index?.plugins?.let { _available.value = it }
                val result = repositoryClient.fetchIndex(cached)
                _available.value = result.index.plugins
                repositoryCache.updateData {
                    SourcePluginRepositoryCache(etag = result.etag, index = result.index)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (_available.value.isEmpty()) {
                    _error.value = e.message ?: e::class.simpleName ?: "Unknown error"
                }
            } finally {
                _isRefreshing.value = false
            }
        }.also { refreshJob = it }
    }

    fun install(entry: SourcePluginIndexEntry): Job = runPluginOperation(entry.id) {
        registry.install(entry)
    }

    fun setEnabled(pluginId: String, enabled: Boolean): Job = runPluginOperation(pluginId) {
        registry.setEnabled(pluginId, enabled)
    }

    fun uninstall(pluginId: String): Job = runPluginOperation(pluginId) {
        registry.uninstall(pluginId)
    }

    fun clearError() {
        _error.value = null
    }

    private fun runPluginOperation(pluginId: String, operation: suspend () -> Unit): Job = scope.launch {
        _busyPluginIds.value += pluginId
        _error.value = null
        try {
            operation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _error.value = e.message ?: e::class.simpleName ?: "Unknown error"
        } finally {
            _busyPluginIds.value -= pluginId
        }
    }
}
