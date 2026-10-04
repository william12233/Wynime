/*
 * Copyright (C) 2026 Wynime contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/william12233/Wynime/blob/main/LICENSE
 */

package me.him188.ani.app.platform

/** Public Wynime endpoints shared by the app, updater, and settings UI. */
object WynimeBrand {
    const val name = "Wynime"
    const val repository = "william12233/Wynime"
    const val githubHome = "https://github.com/$repository"
    const val githubIssues = "$githubHome/issues"
    const val githubContributors = "$githubHome/graphs/contributors"
    const val githubReleases = "$githubHome/releases"
    const val githubReleaseTagPrefix = "$githubReleases/tag/"
    const val githubReleasesApi = "https://api.github.com/repos/$repository/releases"
    const val sourcePluginsRawBase =
        "https://raw.githubusercontent.com/william12233/Wynime/main/source/plugins"
}
