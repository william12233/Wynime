/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.sourceplugin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.io.files.Path
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.source.plugin.api.SourcePlugin
import me.him188.ani.source.plugin.api.SourcePluginContext
import me.him188.ani.source.plugin.api.SourcePluginMetadata
import me.him188.ani.source.plugin.api.SourceResolveRequest
import me.him188.ani.source.plugin.api.ResolvedMedia
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger

data class SourcePluginRuntimeState(
    val installed: InstalledSourcePlugin,
    val metadata: SourcePluginMetadata? = null,
    val errorMessage: String? = null,
)

class SourcePluginRegistry(
    private val installedRepository: InstalledSourcePluginRepository,
    private val installer: SourcePluginInstaller,
    private val loader: SourcePluginLoader,
    private val contextFactory: SourcePluginContextFactory,
) : AutoCloseable {
    private val logger = logger<SourcePluginRegistry>()
    private val loaded = LinkedHashMap<String, LoadedSourcePlugin>()
    private val _states = MutableStateFlow<List<SourcePluginRuntimeState>>(emptyList())

    val states: StateFlow<List<SourcePluginRuntimeState>> = _states.asStateFlow()
    val mediaSources: Flow<List<MediaSource>> = states.map { current ->
        current.mapNotNull { state ->
            if (state.installed.enabled && state.metadata != null && state.errorMessage == null) {
                loaded[state.installed.id]?.plugin?.let { plugin ->
                    SourcePluginMediaSource(plugin)
                }
            } else {
                null
            }
        }
    }

    suspend fun loadInstalled() {
        closeLoadedPlugins()
        val current = installedRepository.snapshot().plugins
        _states.value = current.map { installed ->
            if (!installed.enabled) {
                SourcePluginRuntimeState(installed)
            } else {
                runCatching { load(installed) }
                    .fold(
                        onSuccess = { loadedPlugin ->
                            SourcePluginRuntimeState(installed, loadedPlugin.plugin.metadata)
                        },
                        onFailure = { throwable ->
                            logger.error(throwable) { "Failed to load source plugin ${installed.id}" }
                            SourcePluginRuntimeState(installed, errorMessage = throwable.message)
                        },
                    )
            }
        }
    }

    suspend fun install(entry: SourcePluginIndexEntry): InstalledSourcePlugin {
        val installed = installer.install(entry) { candidate ->
            val loadedCandidate = loadUnregistered(candidate)
            loadedCandidate.close()
        }
        loadInstalled()
        return installed
    }

    suspend fun setEnabled(pluginId: String, enabled: Boolean) {
        installedRepository.setEnabled(pluginId, enabled)
        loadInstalled()
    }

    suspend fun uninstall(pluginId: String) {
        loaded.remove(pluginId)?.close()
        try {
            installer.uninstall(pluginId)
        } finally {
            // Reconcile the in-memory registry even when filesystem or datastore
            // cleanup fails. A failed uninstall must remain visible as an error,
            // rather than leaving a stale source list in memory.
            loadInstalled()
        }
    }

    suspend fun resolve(request: SourceResolveRequest): ResolvedMedia {
        val plugin = loaded[requestPluginId(request)]?.plugin
            ?: throw IllegalStateException("Source plugin is not loaded: ${requestPluginId(request)}")
        return plugin.resolve(request)
    }

    fun plugin(pluginId: String): SourcePlugin? = loaded[pluginId]?.plugin

    override fun close() {
        closeLoadedPlugins()
    }

    private fun load(installed: InstalledSourcePlugin): LoadedSourcePlugin {
        val loadedPlugin = loadUnregistered(installed)
        loaded[installed.id]?.close()
        loaded[installed.id] = loadedPlugin
        return loadedPlugin
    }

    private fun loadUnregistered(installed: InstalledSourcePlugin): LoadedSourcePlugin {
        val context: SourcePluginContext = contextFactory.create(installed.id)
        val loadedPlugin = loader.load(
            artifact = Path(installed.artifactPath).inSystem,
            entryClass = installed.manifest.entryClass,
            context = context,
        )
        return try {
            loadedPlugin.also { loaded ->
                check(loaded.plugin.metadata.id == installed.id) {
                    "Plugin metadata id ${loaded.plugin.metadata.id} does not match ${installed.id}"
                }
                check(loaded.plugin.metadata.version == installed.version) {
                    "Plugin metadata version does not match ${installed.id}"
                }
            }
        } catch (throwable: Throwable) {
            loadedPlugin.close()
            throw throwable
        }
    }

    private fun requestPluginId(request: SourceResolveRequest): String {
        return request.pluginId.takeIf { it.isNotBlank() }
            ?: error("SourceResolveRequest does not carry a plugin id")
    }

    private fun closeLoadedPlugins() {
        loaded.values.forEach { runCatching { it.close() } }
        loaded.clear()
    }

}
