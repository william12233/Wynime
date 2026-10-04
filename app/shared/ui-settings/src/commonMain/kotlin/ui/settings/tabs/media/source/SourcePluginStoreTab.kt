/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.domain.sourceplugin.SourcePluginRuntimeState
import me.him188.ani.app.domain.sourceplugin.compareSourcePluginVersions
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem

private object SourcePluginStoreTestTags {
    const val REFRESH = "source_plugin_store_refresh"
    const val INSTALL_PREFIX = "source_plugin_store_install_"
    const val UNINSTALL_PREFIX = "source_plugin_store_uninstall_"
}

@Composable
fun SettingsScope.SourcePluginStoreTab(state: SourcePluginStoreState) {
    val available by state.available.collectAsStateWithLifecycle()
    val installed by state.installed.collectAsStateWithLifecycle()
    val busyPluginIds by state.busyPluginIds.collectAsStateWithLifecycle()
    val isRefreshing by state.isRefreshing.collectAsStateWithLifecycle()
    val error by state.error.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        state.refresh()
    }

    Group(
        title = { Text("來源插件商店") },
        description = { Text("從官方索引安裝可執行的影片來源插件；播放器與下載器會在使用時重新解析短期媒體網址。") },
        actions = {
            IconButton(
                onClick = { state.refresh() },
                enabled = !isRefreshing,
                modifier = Modifier.testTag(SourcePluginStoreTestTags.REFRESH),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "重新整理來源插件")
            }
        },
    ) {
        if (isRefreshing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        error?.let { message ->
            TextItem(
                description = { Text(message) },
                action = {
                    TextButton(onClick = { state.refresh() }) { Text("重試") }
                },
                title = { Text("索引或安裝失敗") },
            )
            HorizontalDividerItem()
        }
        if (!isRefreshing && available.isEmpty() && error == null) {
            TextItem(
                title = { Text("目前沒有可用插件") },
                description = { Text("按右上角重新整理以讀取官方插件索引。") },
            )
        }
        available.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDividerItem()
            val current = installed.firstOrNull { it.installed.id == entry.id }
            val busy = entry.id in busyPluginIds
            val updateAvailable = current != null && compareSourcePluginVersions(entry.version, current.installed.version) > 0
            val repairNeeded = current?.errorMessage != null
            TextItem(
                title = { Text(entry.displayName) },
                description = {
                    Column {
                        Text("${entry.id} · v${entry.version}")
                        if (entry.description.isNotBlank()) Text(entry.description)
                        Text(
                            "網站：${entry.website}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                icon = entry.icon?.let { iconUrl ->
                    {
                        AsyncImage(
                            iconUrl,
                            contentDescription = entry.displayName,
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    }
                },
                action = {
                    TextButton(
                        onClick = { state.install(entry) },
                        enabled = !busy && (current == null || updateAvailable || repairNeeded),
                        modifier = Modifier.testTag(SourcePluginStoreTestTags.INSTALL_PREFIX + entry.id),
                    ) {
                        Text(
                            when {
                                current == null -> "安裝"
                                repairNeeded -> "重新安裝"
                                updateAvailable -> "更新"
                                else -> "已是最新"
                            },
                        )
                    }
                },
            )
        }
    }

    Group(title = { Text("已安裝插件") }) {
        if (installed.isEmpty()) {
            TextItem(
                title = { Text("尚未安裝來源插件") },
                description = { Text("安裝後插件會出現在影片來源選擇中。") },
            )
        } else {
            installed.forEachIndexed { index, runtime ->
                if (index > 0) HorizontalDividerItem()
                InstalledSourcePluginItem(
                    runtime = runtime,
                    busy = runtime.installed.id in busyPluginIds,
                    onEnabledChange = { state.setEnabled(runtime.installed.id, it) },
                    onUninstall = { state.uninstall(runtime.installed.id) },
                )
            }
        }
    }
}

@Composable
private fun SettingsScope.InstalledSourcePluginItem(
    runtime: SourcePluginRuntimeState,
    busy: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onUninstall: () -> Unit,
) {
    val plugin = runtime.installed
    TextItem(
        title = { Text(runtime.metadata?.displayName ?: plugin.manifest.displayName) },
        description = {
            Column {
                Text(
                    runtime.errorMessage
                        ?: "${plugin.id} · v${plugin.version}${if (plugin.enabled) " · 啟用" else " · 停用"}",
                )
                Text(
                    "網站：${runtime.metadata?.website ?: plugin.manifest.website}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        icon = (runtime.metadata?.iconUrl ?: plugin.manifest.icon)?.let { iconUrl ->
            {
                AsyncImage(
                    iconUrl,
                    contentDescription = runtime.metadata?.displayName ?: plugin.manifest.displayName,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
        },
        action = {
            Row {
                androidx.compose.material3.Switch(
                    checked = plugin.enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = !busy && runtime.errorMessage == null,
                )
                IconButton(
                    onClick = onUninstall,
                    enabled = !busy,
                    modifier = Modifier.testTag(SourcePluginStoreTestTags.UNINSTALL_PREFIX + plugin.id),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = "解除安裝 ${plugin.id}")
                }
            }
        },
    )
}
