/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.sourceplugin

import me.him188.ani.app.platform.Context
import me.him188.ani.source.plugin.api.SourcePluginContext
import me.him188.ani.utils.io.SystemPath

actual fun createSourcePluginLoader(context: Context): SourcePluginLoader = IosSourcePluginLoader

private object IosSourcePluginLoader : SourcePluginLoader {
    override fun load(
        artifact: SystemPath,
        entryClass: String,
        context: SourcePluginContext,
    ): LoadedSourcePlugin = throw UnsupportedSourcePluginException(
        "Runtime source plugin loading is unsupported on iOS",
    )
}
