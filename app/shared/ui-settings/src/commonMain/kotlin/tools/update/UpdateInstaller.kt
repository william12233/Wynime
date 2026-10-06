/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.extension
import kotlin.coroutines.cancellation.CancellationException

/**
 * 安装包安装器
 *
 * - 安卓：弹出系统 APK 安装界面
 * - Windows：使用脚本自动覆盖安装 （一键）
 * - macOS：打开 dmg 让系统去安装，需要用户手动拖拽一下
 */
interface UpdateInstaller {
    /**
     * 当前平台可由 [install] 直接安装的本地安装包扩展名 (小写, 不含点).
     *
     * 为空表示不支持安装本地安装包, 例如 Linux 只能通过 AppImage 差分更新安装.
     */
    val installablePackageExtensions: Set<String> get() = emptySet()

    /**
     * [file] 是否为当前平台可由 [install] 直接安装的本地安装包. 仅根据扩展名判断, 不校验文件内容.
     */
    fun isInstallablePackage(file: SystemPath): Boolean =
        file.extension.lowercase() in installablePackageExtensions

    /**
     * 返回安装前需要下载的文件地址.
     *
     * 默认直接使用版本 API 返回的安装包地址. Linux AppImage 将其转换为较小的 zsync 元数据地址,
     * 实际差分数据由外部更新器在安装阶段下载.
     */
    fun getUpdatePreparationUrls(packageUrls: List<String>): List<String> = packageUrls

    /**
     * 如果 [install] 可能返回 [InstallationResult.Failed], 则需实现
     */
    suspend fun openForManualInstallation(file: SystemPath, context: ContextMP): Boolean = false

    fun install(file: SystemPath, context: ContextMP): InstallationResult

    /**
     * Installs a package whose exact release asset is known. Platforms that can validate the
     * package before launching their installer should override this overload.
     */
    fun install(
        file: SystemPath,
        context: ContextMP,
        packageDescriptor: UpdatePackageDescriptor?,
    ): InstallationResult = install(file, context)

    /** Returns whether the platform-specific installation permission is currently available. */
    fun isInstallPermissionGranted(context: ContextMP): Boolean = true

    /** Returns a persisted installation request, if the platform keeps one across resume. */
    fun pendingInstallation(): PendingInstallation? = null

    /** Context-aware variant used when the platform needs an application context to restore state. */
    fun pendingInstallation(context: ContextMP): PendingInstallation? = pendingInstallation()

    fun clearPendingInstallation() = Unit

    /** Validates an already downloaded package for safe reuse. */
    fun isValidDownloadedPackage(
        file: SystemPath,
        packageDescriptor: UpdatePackageDescriptor,
        context: ContextMP,
    ): Boolean = false

    /**
     * 使用版本 API 返回的原始安装包地址执行安装.
     *
     * 默认平台仍安装已经下载到 [file] 的完整安装包.
     */
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

    /** Leaves a permission-waiting state without claiming that installation succeeded. */
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
    data object Succeed : InstallationResult() // 实际上可能不会返回, 因为安装成功会重启

    /** The APK is valid, but Android still needs the user to grant install-from-this-source. */
    data object RequiresInstallPermission : InstallationResult()

    /**
     * 安装失败, 附带失败原因. UI 会展示这个失败原因
     */
    data class Failed(
        val reason: InstallationFailureReason,
        val message: String? = null,
    ) : InstallationResult()
}

enum class InstallationFailureReason {
    /**
     * 未支持的安装目录结构. 例如 Windows 上未找到 `Wynime.exe`
     */
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
