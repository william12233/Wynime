package com.wynime.app.ui.update

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.UriHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import com.wynime.app.data.repository.RepositoryNetworkException
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.domain.foundation.HttpClientProvider
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.domain.foundation.get
import com.wynime.app.domain.update.UpdateManager
import com.wynime.app.platform.ContextMP
import com.wynime.app.platform.WynimeBrand
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.app.tools.MonoTasker
import com.wynime.app.tools.update.DefaultFileDownloader
import com.wynime.app.tools.update.FileDownloaderState
import com.wynime.app.tools.update.InstallationResult
import com.wynime.app.tools.update.UpdateInstallationRunner
import com.wynime.app.tools.update.UpdateInstallationState
import com.wynime.app.tools.update.UpdateInstaller
import com.wynime.app.tools.update.UpdatePackageDescriptor
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.utils.io.createDirectories
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.resolve
import com.wynime.utils.logging.info
import com.wynime.utils.logging.warn
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.utils.platform.currentTimeMillis
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException

@Stable
class AppUpdateViewModel : AbstractViewModel(), KoinComponent {
    private val settingsRepository: SettingsRepository by inject()
    private val updateSettings = settingsRepository.updateSettings.flow
    private val updateManager: UpdateManager by inject()
    private val clientProvider: HttpClientProvider by inject()
    private val updateInstaller: UpdateInstaller by inject()
    private val installationRunner by lazy { UpdateInstallationRunner(updateInstaller) }

    private val fileDownloader by lazy { DefaultFileDownloader(clientProvider.get()) }
    private val updateChecker: UpdateChecker = UpdateChecker(clientProvider.get())

    private val latestVersionFlow = MutableStateFlow<NewVersion?>(null)
    private val lastCheckTime: MutableStateFlow<Long> = MutableStateFlow(0L)

    private val fileDownloaderPresenter = FileDownloaderPresenter(fileDownloader, backgroundScope)
    private val autoCheckTasker = MonoTasker(backgroundScope)

    private val installationTasker = MonoTasker(backgroundScope)
    private val checkUpdateErrorFlow = MutableStateFlow<LoadError?>(null)

    val presentationFlow = combine(
        latestVersionFlow,
        fileDownloaderPresenter.flow,
        autoCheckTasker.isRunning,
        installationRunner.state,
        checkUpdateErrorFlow,
    ) { latestVersion, fileDownloaderStats, isCheckingUpdate, installationState, checkUpdateError ->
        val latestVersion = latestVersion
        val state = when {

            lastCheckTime.value == 0L -> AppUpdateState.ClickToCheck
            latestVersion == null -> AppUpdateState.AlreadyUpToDate
            installationState is UpdateInstallationState.Installing -> AppUpdateState.Installing(latestVersion)
            installationState is UpdateInstallationState.WaitingForPermission ->
                AppUpdateState.WaitingForPermission(latestVersion)
            else -> {
                when (fileDownloaderStats.state) {
                    FileDownloaderState.Idle -> AppUpdateState.HasUpdate(latestVersion)
                    is FileDownloaderState.Failed ->
                        AppUpdateState.DownloadFailed(latestVersion, fileDownloaderStats.state.throwable)

                    FileDownloaderState.Downloading ->
                        AppUpdateState.Downloading(latestVersion, fileDownloaderStats)

                    is FileDownloaderState.Succeed ->
                        AppUpdateState.Downloaded(latestVersion, fileDownloaderStats.state.file)

                    is FileDownloaderState.Cancelled -> {

                        AppUpdateState.ClickToCheck
                    }
                }
            }
        }

        AppUpdatePresentation(
            newVersion = latestVersion,
            state = state,
            fileDownloaderStats = fileDownloaderStats,
            isCheckingUpdate = isCheckingUpdate,
            checkUpdateError = checkUpdateError,
            installationFailure = (installationState as? UpdateInstallationState.Failed)?.result,
            isPlaceholder = latestVersion == null && fileDownloaderStats.isPlaceholder,
        )
    }.stateIn(
        scope = backgroundScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = AppUpdatePresentation.Placeholder,
    )

    val isChecking get() = autoCheckTasker.isRunning.value
    private val downloadTasker = MonoTasker(backgroundScope)

    fun startAutomaticCheckLatestVersion() {
        if (autoCheckTasker.isRunning.value) {
            return
        } else {
            if (currentTimeMillis() - lastCheckTime.value < 1000 * 60 * 60 * 1) {
                return
            }

            startCheckLatestVersion(null)
        }
    }

    fun startCheckLatestVersion(
        uriHandler: UriHandler?
    ) {
        autoCheckTasker.launch {
            val updateSettings = updateSettings.first()

            checkUpdateErrorFlow.value = null
            val ver = try {
                if (!updateSettings.autoCheckUpdate) {
                    logger.info { "autoCheckUpdate disabled" }
                    return@launch
                }
                logger.info { "Checking latest version, updateSettings=${updateSettings}" }

                updateChecker.checkLatestVersion(updateSettings.releaseClass)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                checkUpdateErrorFlow.value = LoadError.fromException(e)
                logger.info { "Auto update checking failed due to IOException: $e" }
                return@launch
            } finally {
                lastCheckTime.value = currentTimeMillis()
            }

            latestVersionFlow.update { ver }

            if (ver != null && updateSettings.autoDownloadUpdate) {
                logger.info { "autoDownloadUpdate is true, starting download" }
                startDownload(ver, uriHandler)
            }
        }
    }

    fun startDownload(ver: NewVersion, uriHandler: UriHandler?, context: ContextMP? = null) {
        downloadTasker.launch {
            val settings = updateSettings.first()
            if (!settings.inAppDownload) {
                if (uriHandler == null) {
                    logger.warn { "uriHandler is null, cannot navigate to browser (may happen for auto check)" }
                    return@launch
                }
                uriHandler.openUri(WynimeBrand.githubReleaseTagPrefix + ver.name)
                return@launch
            }

            val preparationUrls = updateInstaller.getUpdatePreparationUrls(ver.downloadUrlAlternatives)
            val dir = updateManager.saveDir

            val keepFilenames = preparationUrls.map {
                it.substringAfterLast("/", "")
            }.let { list ->
                list + list.map { "$it.sha1" }
            }
            withContext(Dispatchers.IO) {
                updateManager.deleteStaleInstallers(keepFilenames)
                dir.createDirectories()
            }
            val descriptor = ver.packageDescriptor
            if (context != null && descriptor != null) {
                val existing = dir.resolve(descriptor.filename)
                if (updateInstaller.isValidDownloadedPackage(existing, descriptor, context)) {
                    fileDownloader.reuse(existing, descriptor.downloadUrl)
                    return@launch
                }
            }
            fileDownloader.download(
                alternativeUrls = preparationUrls,
                filenameProvider = { url ->
                    if (descriptor?.downloadUrl == url) descriptor.filename
                    else url.substringAfterLast("/", "")
                },
                saveDir = dir,
            )
        }
    }

    fun restartDownload(uriHandler: UriHandler, context: ContextMP? = null) {
        latestVersionFlow.value?.let { startDownload(it, uriHandler, context) }
    }

    fun install(context: ContextMP) {
        val state = presentationFlow.value.state as? AppUpdateState.Downloaded
            ?: return
        installationTasker.launch(Dispatchers.Main) {
            installationRunner.install(
                file = state.file,
                packageUrls = state.version.downloadUrlAlternatives,
                context = context,
                packageDescriptor = state.version.packageDescriptor,
            )
        }
    }

    fun onAppResumed(context: ContextMP) {
        val waitingForPermission = installationRunner.state.value is UpdateInstallationState.WaitingForPermission
        val pending = updateInstaller.pendingInstallation(context)
        if (!waitingForPermission && pending == null) return
        if (!updateInstaller.isInstallPermissionGranted(context)) {
            if (waitingForPermission) installationRunner.returnToDownloaded()
            return
        }
        val currentVersion = latestVersionFlow.value
        val file = (fileDownloaderPresenter.flow.value.state as? FileDownloaderState.Succeed)?.file
            ?: pending?.file
            ?: return
        val version = currentVersion ?: pending?.descriptor?.let { descriptor ->
            NewVersion(
                name = descriptor.version,
                changelogs = emptyList(),
                downloadUrlAlternatives = listOf(descriptor.downloadUrl),
                publishedAt = "",
                packageDescriptor = descriptor,
            )
        } ?: return
        installationTasker.launch(Dispatchers.Main) {
            installationRunner.install(
                file = file,
                packageUrls = version.downloadUrlAlternatives,
                context = context,
                packageDescriptor = pending?.descriptor ?: version.packageDescriptor,
            )
        }
    }

    fun dismissInstallationFailure() {
        installationRunner.dismissFailure()
    }

    fun cancelDownload() {
        if (installationTasker.isRunning.value) {
            installationTasker.cancel()
        } else {
            downloadTasker.cancel()
        }
    }
}

@Immutable
data class AppUpdatePresentation(
    val newVersion: NewVersion?,
    val state: AppUpdateState,
    val fileDownloaderStats: FileDownloaderStats,
    val isCheckingUpdate: Boolean,
    val checkUpdateError: LoadError? = null,
    val installationFailure: InstallationResult.Failed? = null,
    val currentVersion: String = currentWynimeBuildConfig.versionName,
    val isPlaceholder: Boolean = false,
) {
    val isDownloading = when (state) {
        AppUpdateState.AlreadyUpToDate -> false
        AppUpdateState.ClickToCheck -> false
        is AppUpdateState.DownloadFailed -> true
        is AppUpdateState.Downloaded -> true
        is AppUpdateState.Downloading -> true
        is AppUpdateState.HasUpdate -> false
        is AppUpdateState.Installing -> true
        is AppUpdateState.WaitingForPermission -> true
    }
    val downloadError = (state as? AppUpdateState.DownloadFailed)?.throwable?.let { LoadError.fromException(it) }

    val hasUpdate = state is AppUpdateState.HasUpdate

    companion object {
        val Placeholder = AppUpdatePresentation(
            newVersion = null,
            state = AppUpdateState.ClickToCheck,
            fileDownloaderStats = FileDownloaderStats.Placeholder,
            isCheckingUpdate = false,
            isPlaceholder = true,
        )
    }
}

@Immutable
class NewVersion(
    val name: String,
    val changelogs: List<Changelog>,

    val downloadUrlAlternatives: List<String>,
    val publishedAt: String,
    val packageDescriptor: UpdatePackageDescriptor? = null,
) {
    val majorChanges = changelogs.asSequence().flatMap { changelog ->
        changelog.changes.lineSequence()
            .filterNot { it.isBlank() }
            .map { it.removePrefix("- ").removePrefix("* ") }
    }.take(4).toList()
}

@Immutable
class Changelog(
    val version: String,
    val publishedAt: String,
    changes: String
) {
    val changes = changes.lineSequence()
        .filterNot {
            it.startsWith("**Full Changelog**: ", ignoreCase = true)
                    || it.startsWith("Full Changelog:", ignoreCase = true)
        }
        .joinToString("\n")
        .trim()
}

@TestOnly
val TestNewVersion
    get() = NewVersion(
        "1.0.0",
        listOf(
            Changelog(
                "1.0.0", "",
                "- Major feature 1\n- Major feature 2",
            ),
        ),
        listOf("https://example.com"),
        "2024-01-02",
    )

@TestOnly
object TestAppUpdatePresentations {
    @TestOnly
    val HasUpdate
        get() = AppUpdatePresentation(
            newVersion = TestNewVersion,
            state = AppUpdateState.HasUpdate(TestNewVersion),
            fileDownloaderStats = FileDownloaderStats.Placeholder,
            isCheckingUpdate = false,
        )

    @TestOnly
    val Downloading
        get() = AppUpdatePresentation(
            newVersion = TestNewVersion,
            state = AppUpdateState.Downloading(TestNewVersion, TestFileDownloaderStats.Downloading),
            fileDownloaderStats = FileDownloaderStats.Placeholder,
            isCheckingUpdate = false,
        )

    @TestOnly
    val Succeed
        get() = AppUpdatePresentation(
            newVersion = TestNewVersion,
            state = AppUpdateState.Downloaded(TestNewVersion, kotlinx.io.files.Path("").inSystem),
            fileDownloaderStats = FileDownloaderStats.Placeholder,
            isCheckingUpdate = false,
        )

    @TestOnly
    val Failed
        get() = AppUpdatePresentation(
            newVersion = TestNewVersion,
            state = AppUpdateState.DownloadFailed(TestNewVersion, RepositoryNetworkException()),
            fileDownloaderStats = FileDownloaderStats.Placeholder,
            isCheckingUpdate = false,
        )
}
