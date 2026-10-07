package com.wynime.app.domain.sourceplugin

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class InstalledSourcePluginRepository(
    private val store: DataStore<InstalledSourcePlugins>,
) {
    val flow: Flow<InstalledSourcePlugins> = store.data

    suspend fun snapshot(): InstalledSourcePlugins = flow.first()

    suspend fun upsert(plugin: InstalledSourcePlugin) {
        store.updateData { current ->
            current.copy(
                plugins = current.plugins.filterNot { it.id == plugin.id } + plugin,
            )
        }
    }

    suspend fun setEnabled(pluginId: String, enabled: Boolean): InstalledSourcePlugin? {
        var result: InstalledSourcePlugin? = null
        store.updateData { current ->
            current.copy(
                plugins = current.plugins.map { plugin ->
                    if (plugin.id == pluginId) plugin.copy(enabled = enabled).also { result = it } else plugin
                },
            )
        }
        return result
    }

    suspend fun remove(pluginId: String): InstalledSourcePlugin? {
        var removed: InstalledSourcePlugin? = null
        store.updateData { current ->
            current.copy(
                plugins = current.plugins.filterNot { plugin ->
                    if (plugin.id == pluginId) {
                        removed = plugin
                        true
                    } else {
                        false
                    }
                },
            )
        }
        return removed
    }
}
