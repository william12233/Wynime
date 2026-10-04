/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.sourceplugin

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
