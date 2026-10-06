/*
 * Copyright (C) 2026 Wynime contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 * https://github.com/wynime-app/Wynime/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import me.him188.ani.utils.io.SystemPath

/** The exact release asset selected for the current platform and ABI. */
data class UpdatePackageDescriptor(
    val version: String,
    val filename: String,
    val downloadUrl: String,
    val abi: String? = null,
)

data class PendingInstallation(
    val file: SystemPath,
    val descriptor: UpdatePackageDescriptor?,
)
