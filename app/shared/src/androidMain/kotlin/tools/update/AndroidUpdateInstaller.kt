package com.wynime.app.tools.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.wynime.app.platform.ContextMP
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.exists
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.length
import com.wynime.utils.io.toFile
import com.wynime.utils.io.toKtPath
import java.util.Locale
import java.io.File

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

class AndroidUpdateInstaller : UpdateInstaller {
    private companion object {
        private const val PREFERENCES = "pending_update_installation"
        private const val FILE_PATH = "file_path"
        private const val VERSION = "version"
        private const val FILENAME = "filename"
        private const val DOWNLOAD_URL = "download_url"
        private const val ABI = "abi"
    }

    override fun install(file: SystemPath, context: ContextMP): InstallationResult {
        return install(file, context, packageDescriptor = null)
    }

    override fun install(
        file: SystemPath,
        context: ContextMP,
        packageDescriptor: UpdatePackageDescriptor?,
    ): InstallationResult {
        val validation = validate(file, context, packageDescriptor)
        if (validation != null) {
            clearPendingInstallation()
            return validation
        }

        if (!context.packageManager.canRequestPackageInstalls()) {
            savePendingInstallation(context, file, packageDescriptor)
            return try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                InstallationResult.RequiresInstallPermission
            } catch (e: Throwable) {
                clearPendingInstallation()
                InstallationResult.Failed(
                    InstallationFailureReason.INSTALL_PERMISSION_REQUEST_FAILED,
                    e.message ?: e::class.simpleName,
                )
            }
        }

        return try {
            installApk(context, file.toFile())
            clearPendingInstallation()
            InstallationResult.Succeed
        } catch (e: ApkInstallException) {
            clearPendingInstallation()
            InstallationResult.Failed(e.reason, e.message)
        } catch (e: Throwable) {
            clearPendingInstallation()
            InstallationResult.Failed(
                InstallationFailureReason.START_ACTIVITY_FAILED,
                e.message ?: e::class.simpleName,
            )
        }
    }

    override suspend fun install(
        file: SystemPath,
        packageUrls: List<String>,
        context: ContextMP,
        packageDescriptor: UpdatePackageDescriptor?,
    ): InstallationResult = install(file, context, packageDescriptor)

    override fun isInstallPermissionGranted(context: ContextMP): Boolean =
        context.packageManager.canRequestPackageInstalls()

    override fun pendingInstallation(): PendingInstallation? {
        return pendingInstallation(lastContext ?: return null)
    }

    override fun pendingInstallation(context: ContextMP): PendingInstallation? {
        lastContext = context.applicationContext
        val preferences = pendingPreferences() ?: return null
        val path = preferences.getString(FILE_PATH, null) ?: return null
        val descriptor = preferences.let { preferences ->
            val version = preferences.getString(VERSION, null)
            val filename = preferences.getString(FILENAME, null)
            val url = preferences.getString(DOWNLOAD_URL, null)
            if (version != null && filename != null && url != null) {
                UpdatePackageDescriptor(version, filename, url, preferences.getString(ABI, null))
            } else null
        }
        return PendingInstallation(File(path).toKtPath().inSystem, descriptor)
    }

    override fun clearPendingInstallation() {

        lastContext?.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
    }

    override fun isValidDownloadedPackage(
        file: SystemPath,
        packageDescriptor: UpdatePackageDescriptor,
        context: ContextMP,
    ): Boolean = validate(file, context, packageDescriptor) == null

    private var lastContext: Context? = null

    private fun pendingPreferences(): android.content.SharedPreferences? =
        lastContext?.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun savePendingInstallation(
        context: Context,
        file: SystemPath,
        descriptor: UpdatePackageDescriptor?,
    ) {
        lastContext = context.applicationContext
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(FILE_PATH, file.toFile().absolutePath)
            .apply {
                if (descriptor == null) {
                    remove(VERSION)
                    remove(FILENAME)
                    remove(DOWNLOAD_URL)
                    remove(ABI)
                } else {
                    putString(VERSION, descriptor.version)
                    putString(FILENAME, descriptor.filename)
                    putString(DOWNLOAD_URL, descriptor.downloadUrl)
                    descriptor.abi?.let { putString(ABI, it) } ?: remove(ABI)
                }
            }
            .apply()
    }

    private fun validate(
        file: SystemPath,
        context: Context,
        descriptor: UpdatePackageDescriptor?,
    ): InstallationResult.Failed? {
        lastContext = context.applicationContext
        val apk = file.toFile()
        if (!file.exists() || !apk.isFile || file.length() <= 0) {
            return InstallationResult.Failed(
                InstallationFailureReason.FILE_NOT_FOUND,
                "APK does not exist or is empty: ${apk.absolutePath}",
            )
        }
        if (!apk.name.lowercase(Locale.ROOT).endsWith(".apk")) {
            return InstallationResult.Failed(InstallationFailureReason.INVALID_APK, "File extension is not .apk")
        }
        if (descriptor != null) {
            val expectedFilenamePattern = Regex(
                "wynime-${Regex.escape(descriptor.version)}-(arm64-v8a|armeabi-v7a|x86_64)\\.apk",
            )
            if (
                apk.name != descriptor.filename ||
                !expectedFilenamePattern.matches(descriptor.filename) ||
                !descriptor.filename.endsWith(".apk")
            ) {
                return InstallationResult.Failed(
                    InstallationFailureReason.INVALID_APK,
                    "Unexpected release asset filename: ${apk.name}",
                )
            }
            val expectedAbi = descriptor.abi
            if (expectedAbi == null || expectedAbi !in android.os.Build.SUPPORTED_ABIS) {
                return InstallationResult.Failed(
                    InstallationFailureReason.ABI_MISMATCH,
                    "Asset ABI $expectedAbi is not supported by ${android.os.Build.SUPPORTED_ABIS.joinToString()}",
                )
            }
            if (descriptor.filename != "wynime-${descriptor.version}-$expectedAbi.apk") {
                return InstallationResult.Failed(
                    InstallationFailureReason.ABI_MISMATCH,
                    "Asset filename ABI does not match descriptor ABI $expectedAbi",
                )
            }
        }
        val packageInfo = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: return InstallationResult.Failed(
                InstallationFailureReason.INVALID_APK,
                "PackageManager could not parse ${apk.name}",
            )
        if (packageInfo.packageName != context.packageName) {
            return InstallationResult.Failed(
                InstallationFailureReason.PACKAGE_MISMATCH,
                "Expected ${context.packageName}, got ${packageInfo.packageName}",
            )
        }
        if (descriptor != null) {
            if (packageInfo.versionName != descriptor.version) {
                return InstallationResult.Failed(
                    InstallationFailureReason.VERSION_MISMATCH,
                    "Expected version ${descriptor.version}, got ${packageInfo.versionName}",
                )
            }
            val current = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            if (packageInfo.longVersionCode <= current) {
                return InstallationResult.Failed(
                    InstallationFailureReason.VERSION_MISMATCH,
                    "Candidate versionCode ${packageInfo.longVersionCode} is not newer than $current",
                )
            }
        }
        return null
    }

    private fun installApk(
        context: Context,
        file: File,
    ) {
        val apkUri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Throwable) {
            throw ApkInstallException(InstallationFailureReason.FILE_PROVIDER_FAILED, e.message, e)
        }
        val intent = createApkInstallIntent(apkUri, file.name)

        val handlers = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (handlers.isEmpty()) {
            throw ApkInstallException(
                InstallationFailureReason.NO_INSTALLER_ACTIVITY,
                "No activity can handle APK installation",
            )
        }
        handlers
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .forEach { packageName -> context.grantUriPermission(packageName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }

        try {
            context.startActivity(intent)
        } catch (e: Throwable) {
            throw ApkInstallException(InstallationFailureReason.START_ACTIVITY_FAILED, e.message, e)
        }
    }

    private class ApkInstallException(
        val reason: InstallationFailureReason,
        message: String?,
        cause: Throwable? = null,
    ) : Exception(message, cause)
}

internal fun createApkInstallIntent(apkUri: Uri, apkName: String): Intent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(apkUri, APK_MIME_TYPE)
    clipData = ClipData.newRawUri(apkName, apkUri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
}
