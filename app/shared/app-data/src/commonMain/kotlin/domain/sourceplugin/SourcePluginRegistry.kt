package com.wynime.app.domain.sourceplugin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.io.files.Path
import com.wynime.datasources.api.source.MediaSource
import com.wynime.source.plugin.api.SourcePlugin
import com.wynime.source.plugin.api.SourcePluginContext
import com.wynime.source.plugin.api.SourcePluginMetadata
import com.wynime.source.plugin.api.SourceResolveRequest
import com.wynime.source.plugin.api.ResolvedMedia
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.isRegularFile
import com.wynime.utils.io.exists
import com.wynime.utils.logging.error
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn

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
    private val bundledPackages: BundledSourcePluginPackages? = null,
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
        migrateBundledPlugins()
        val current = installedRepository.snapshot().plugins
        _states.value = current.map { installed ->
            if (installed.manifest.pluginApiVersion != SOURCE_PLUGIN_API_VERSION) {
                SourcePluginRuntimeState(
                    installed,
                    errorMessage = "來源外掛 API ${installed.manifest.pluginApiVersion} 與此版本不相容，請安裝 API $SOURCE_PLUGIN_API_VERSION 套件",
                )
            } else if (!installed.enabled) {
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
        val validate: suspend (InstalledSourcePlugin) -> Unit = { candidate ->
            val loadedCandidate = loadUnregistered(candidate)
            try {
                validateRuntimeContract(loadedCandidate.plugin, candidate)
            } finally {
                loadedCandidate.close()
            }
        }
        val installed = installer.install(entry, validate)
        loadInstalled()
        return installed
    }

    suspend fun bundledEntries(): List<SourcePluginIndexEntry> {
        val packages = bundledPackages ?: return emptyList()
        return packages.pluginIds.map { id ->
            val manifest = packages.manifest(id)
            SourcePluginIndexEntry(
                id = id,
                displayName = manifest.displayName,
                version = manifest.version,
                description = manifest.description,
                website = manifest.website,
                icon = manifest.icon,
                platforms = manifest.platforms,
                manifest = "manifests/$id.json",
            )
        }
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
        check(installed.manifest.pluginApiVersion == SOURCE_PLUGIN_API_VERSION) {
            "Incompatible source plugin API ${installed.manifest.pluginApiVersion}"
        }
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
                check(loaded.plugin.metadata.pluginApiVersion == installed.manifest.pluginApiVersion) {
                    "Plugin metadata API ${loaded.plugin.metadata.pluginApiVersion} does not match " +
                        "manifest API ${installed.manifest.pluginApiVersion}"
                }
                check(loaded.plugin.metadata.pluginApiVersion == SOURCE_PLUGIN_API_VERSION) {
                    "Plugin ${installed.id} requires unsupported plugin API " +
                        loaded.plugin.metadata.pluginApiVersion
                }
            }
        } catch (throwable: Throwable) {
            loadedPlugin.close()
            throw throwable
        }
    }

    private suspend fun migrateBundledPlugins() {
        val packages = bundledPackages ?: return
        for (installed in installedRepository.snapshot().plugins) {
            if (installed.manifest.pluginApiVersion == SOURCE_PLUGIN_API_VERSION &&
                Path(installed.artifactPath).inSystem.let { it.exists() && it.isRegularFile() }
            ) continue
            if (installed.id !in packages.pluginIds) continue
            try {
                val manifest = packages.manifest(installed.id)
                check(manifest.id == installed.id)
                installer.installBundled(manifest, { artifact -> packages.artifact(installed.id, artifact) }) { candidate ->
                    val loadedCandidate = loadUnregistered(candidate)
                    loadedCandidate.close()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logger.warn(error) { "Source plugin ${installed.id} migration failed; keeping its installed files for retry" }
            }
        }
    }

    private suspend fun validateRuntimeContract(
        plugin: SourcePlugin,
        installed: InstalledSourcePlugin,
    ) {
        try {
            val health = plugin.checkConnection()
            logger.info {
                "Source plugin ${installed.id} runtime health: ${health.state.name}" +
                    health.message?.let { ": $it" }.orEmpty()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: LinkageError) {
            throw error
        } catch (error: ClassCastException) {
            throw error
        } catch (error: Error) {
            throw error
        } catch (error: Throwable) {
            logger.warn(error) {
                "Source plugin ${installed.id} runtime health check failed; keeping contract result " +
                    "because the site may be offline"
            }
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
