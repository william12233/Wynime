package com.wynime.app.tools.update

import com.wynime.utils.io.SystemPath

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
