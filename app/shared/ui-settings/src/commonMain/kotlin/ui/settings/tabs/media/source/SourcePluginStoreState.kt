package com.wynime.app.ui.settings.tabs.media.source

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.wynime.app.domain.sourceplugin.SourcePluginIndexEntry
import com.wynime.app.domain.sourceplugin.SourcePluginRepositoryCache
import com.wynime.app.domain.sourceplugin.SourcePluginRegistry
import com.wynime.app.domain.sourceplugin.SourcePluginRepositoryClient
import com.wynime.app.domain.sourceplugin.SOURCE_PLUGIN_API_VERSION
import com.wynime.app.domain.sourceplugin.compareSourcePluginVersions

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
                // Bundled packages are migration-only. New installations must come from
                // the repository so an App release cannot silently select a plugin build.
                val bundled = emptyList<SourcePluginIndexEntry>()
                _available.value = bundled
                val cached = repositoryCache.data.first()
                cached.index?.takeIf { it.pluginApiVersion == SOURCE_PLUGIN_API_VERSION }?.plugins?.let {
                    _available.value = mergeEntries(bundled, it)
                }
                val result = repositoryClient.fetchIndex(cached)
                _available.value = mergeEntries(bundled, result.index.plugins)
                repositoryCache.updateData {
                    SourcePluginRepositoryCache(etag = result.etag, index = result.index)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _error.value = e.message ?: e::class.simpleName ?: "Unknown error"
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

    private fun mergeEntries(
        bundled: List<SourcePluginIndexEntry>,
        remote: List<SourcePluginIndexEntry>,
    ): List<SourcePluginIndexEntry> = (bundled + remote).groupBy { it.id }.values.map { entries ->
        entries.maxWith { left, right -> compareSourcePluginVersions(left.version, right.version) }
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
