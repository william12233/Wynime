package com.wynime.app.tools.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.wynime.app.platform.ContextMP
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.extension
import kotlin.coroutines.cancellation.CancellationException

interface UpdateInstaller {

    val installablePackageExtensions: Set<String> get() = emptySet()

    fun isInstallablePackage(file: SystemPath): Boolean =
        file.extension.lowercase() in installablePackageExtensions

    fun getUpdatePreparationUrls(packageUrls: List<String>): List<String> = packageUrls

    suspend fun openForManualInstallation(file: SystemPath, context: ContextMP): Boolean = false

    fun install(file: SystemPath, context: ContextMP): InstallationResult

    fun install(
        file: SystemPath,
        context: ContextMP,
        packageDescriptor: UpdatePackageDescriptor?,
    ): InstallationResult = install(file, context)

    fun isInstallPermissionGranted(context: ContextMP): Boolean = true

    fun pendingInstallation(): PendingInstallation? = null

    fun pendingInstallation(context: ContextMP): PendingInstallation? = pendingInstallation()

    fun clearPendingInstallation() = Unit

    fun isValidDownloadedPackage(
        file: SystemPath,
        packageDescriptor: UpdatePackageDescriptor,
        context: ContextMP,
    ): Boolean = false

    suspend fun install(
        file: SystemPath,
        packageUrls: List<String>,
        context: ContextMP,
    ): InstallationResult = install(file, context)

    suspend fun install(
        file: SystemPath,
        packageUrls: List<String>,
        context: ContextMP,
        packageDescriptor: UpdatePackageDescriptor?,
    ): InstallationResult = install(file, packageUrls, context)
}

sealed class UpdateInstallationState {
    data object Idle : UpdateInstallationState()
    data object Installing : UpdateInstallationState()
    data object WaitingForPermission : UpdateInstallationState()
    data object Succeed : UpdateInstallationState()
    data class Failed(val result: InstallationResult.Failed) : UpdateInstallationState()
    data class Cancelled(val cause: CancellationException) : UpdateInstallationState()
}

class UpdateInstallationRunner(
    private val installer: UpdateInstaller,
) {
    private val _state = MutableStateFlow<UpdateInstallationState>(UpdateInstallationState.Idle)
    val state: StateFlow<UpdateInstallationState> = _state.asStateFlow()

    suspend fun install(
        file: SystemPath,
        packageUrls: List<String>,
        context: ContextMP,
        packageDescriptor: UpdatePackageDescriptor? = null,
    ) {
        _state.value = UpdateInstallationState.Installing
        try {
            _state.value = when (val result = installer.install(file, packageUrls, context, packageDescriptor)) {
                InstallationResult.Succeed -> UpdateInstallationState.Succeed
                InstallationResult.RequiresInstallPermission -> UpdateInstallationState.WaitingForPermission
                is InstallationResult.Failed -> UpdateInstallationState.Failed(result)
            }
        } catch (e: CancellationException) {
            _state.value = UpdateInstallationState.Cancelled(e)
            throw e
        } catch (e: Throwable) {
            _state.value = UpdateInstallationState.Failed(
                InstallationResult.Failed(
                    InstallationFailureReason.FAILED_TO_COPY,
                    e.message,
                ),
            )
        }
    }

    fun returnToDownloaded() {
        _state.update { state ->
            if (state is UpdateInstallationState.WaitingForPermission) {
                UpdateInstallationState.Idle
            } else {
                state
            }
        }
    }

    fun dismissFailure() {
        _state.update { state ->
            if (state is UpdateInstallationState.Failed) UpdateInstallationState.Idle else state
        }
    }
}

sealed class InstallationResult {
    data object Succeed : InstallationResult()

    data object RequiresInstallPermission : InstallationResult()

    data class Failed(
        val reason: InstallationFailureReason,
        val message: String? = null,
    ) : InstallationResult()
}

enum class InstallationFailureReason {

    UNSUPPORTED_FILE_STRUCTURE,

    FAILED_TO_MOUNT_DMG,
    FAILED_TO_COPY,
    FILE_NOT_FOUND,
    INVALID_APK,
    PACKAGE_MISMATCH,
    VERSION_MISMATCH,
    ABI_MISMATCH,
    FILE_PROVIDER_FAILED,
    NO_INSTALLER_ACTIVITY,
    START_ACTIVITY_FAILED,
    INSTALL_PERMISSION_REQUEST_FAILED,
}
