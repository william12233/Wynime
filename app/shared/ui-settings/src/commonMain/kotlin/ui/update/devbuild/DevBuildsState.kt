package com.wynime.app.ui.update.devbuild

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import com.wynime.app.platform.ContextMP
import com.wynime.app.tools.MonoTasker
import com.wynime.app.tools.update.InstallationResult
import com.wynime.app.tools.update.InstallationFailureReason
import com.wynime.app.tools.update.UpdateInstaller
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.createDirectories
import com.wynime.utils.io.delete
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.io.moveTo
import com.wynime.utils.io.name
import com.wynime.utils.io.resolve
import com.wynime.utils.logging.error
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.coroutines.cancellation.CancellationException

@Stable
class DevBuildsState(
    private val api: GitHubDevBuildApi,
    val spec: DevBuildPackageSpec,
    private val installer: UpdateInstaller,
    private val saveDir: SystemPath,
    private val getToken: suspend () -> String,
    currentVersionName: String,
    backgroundScope: CoroutineScope,
    private val installDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {

    val currentCommitShortSha: String? = parseMainBranchShortSha(currentVersionName)

    val repository: String get() = api.repository

    private val _listState = MutableStateFlow<DevBuildListState>(DevBuildListState.Idle)
    val listState: StateFlow<DevBuildListState> = _listState.asStateFlow()

    private val refreshTasker = MonoTasker(backgroundScope)

    val isRefreshing: StateFlow<Boolean> get() = refreshTasker.isRunning

    private val _lookupState = MutableStateFlow<DevBuildLookupState>(DevBuildLookupState.Idle)
    val lookupState: StateFlow<DevBuildLookupState> = _lookupState.asStateFlow()

    private val lookupTasker = MonoTasker(backgroundScope)

    private val _installState = MutableStateFlow<DevBuildInstallState>(DevBuildInstallState.Idle)
    val installState: StateFlow<DevBuildInstallState> = _installState.asStateFlow()

    private val installTasker = MonoTasker(backgroundScope)

    fun isCurrentCommit(commit: DevBuildCommit): Boolean {
        val current = currentCommitShortSha ?: return false
        return commit.sha.startsWith(current, ignoreCase = true)
    }

    fun refresh() {
        refreshTasker.launch {
            try {
                val token = getToken().trim().ifEmpty { null }
                val commits = coroutineScope {
                    val commits = async { api.listCommits(token) }
                    val runs = async { api.listWorkflowRuns(token) }
                    val artifacts = spec.artifactNames.map { name ->
                        async { api.listArtifacts(token, name) }
                    }
                    buildDevBuildCommits(commits.await(), runs.await(), artifacts.awaitAll().flatten(), spec)
                }
                _listState.value = DevBuildListState.Loaded(commits)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.error(e) { "Failed to load dev builds" }
                _listState.value = DevBuildListState.Failed(e)
            }
        }
    }

    fun lookup(text: String) {
        lookupTasker.launch {
            val input = parseDevBuildInput(text, repository)
            if (input == null) {
                _lookupState.value = DevBuildLookupState.Failed(DevBuildLookupFailure.Unrecognized)
                return@launch
            }
            _lookupState.value = DevBuildLookupState.Loading
            try {
                _lookupState.value = resolve(input, getToken().trim().ifEmpty { null })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.error(e) { "Failed to look up dev build for input: $text" }
                _lookupState.value = DevBuildLookupState.Failed(DevBuildLookupFailure.Error(e))
            }
        }
    }

    private suspend fun resolve(input: DevBuildInput, token: String?): DevBuildLookupState = when (input) {
        is DevBuildInput.Commit -> DevBuildLookupState.Resolved(
            DevBuildLookupResult.Commit(resolveCommit(token, api.getCommit(token, input.sha))),
        )

        is DevBuildInput.PullRequest -> {
            val pr = api.getPullRequest(token, input.number)
            DevBuildLookupState.Resolved(
                DevBuildLookupResult.Commit(
                    commit = resolveCommit(token, api.getCommit(token, pr.head.sha)),
                    pullRequest = DevBuildPullRequest(
                        number = pr.number,
                        title = pr.title,
                        htmlUrl = pr.htmlUrl,
                        headRef = pr.head.ref,
                        isFromFork = pr.head.repo?.fullName?.equals(repository, ignoreCase = true) != true,
                    ),
                ),
            )
        }

        is DevBuildInput.WorkflowRun -> {
            val run = api.getWorkflowRun(token, input.runId)
            val (commit, artifacts) = coroutineScope {
                val commit = async { api.getCommit(token, run.headSha) }
                val artifacts = async { api.listRunArtifacts(token, run.id) }
                commit.await() to artifacts.await()
            }
            DevBuildLookupState.Resolved(
                DevBuildLookupResult.Commit(buildDevBuildCommits(listOf(commit), listOf(run), artifacts, spec).single()),
            )
        }

        is DevBuildInput.Artifact -> {
            val artifact = api.getArtifact(token, input.artifactId)
            if (artifact.name !in spec.candidateArtifactNames) {
                DevBuildLookupState.Failed(DevBuildLookupFailure.ArtifactNotForPlatform(artifact.name))
            } else {
                val runRef = artifact.workflowRun
                    ?: throw IllegalStateException("Artifact ${artifact.id} has no workflow run")
                val (commit, run) = coroutineScope {
                    val commit = async { api.getCommit(token, runRef.headSha) }
                    val run = async { api.getWorkflowRun(token, runRef.id) }
                    commit.await() to run.await()
                }
                DevBuildLookupState.Resolved(
                    DevBuildLookupResult.Commit(
                        buildDevBuildCommits(listOf(commit), listOf(run), listOf(artifact), spec).single(),
                    ),
                )
            }
        }

        is DevBuildInput.PackageUrl -> {
            if (input.fileName.substringAfterLast('.').equals(spec.kind.packageExtension, ignoreCase = true)) {
                DevBuildLookupState.Resolved(DevBuildLookupResult.Package(input.url, input.fileName))
            } else {
                DevBuildLookupState.Failed(DevBuildLookupFailure.UnsupportedPackage(input.fileName))
            }
        }
    }

    private suspend fun resolveCommit(token: String?, commit: GitHubCommit): DevBuildCommit {
        val runs = api.listWorkflowRunsForCommit(token, commit.sha)
        val artifacts = coroutineScope {
            runs.map { run -> async { api.listRunArtifacts(token, run.id) } }.awaitAll().flatten()
        }
        return buildDevBuildCommits(listOf(commit), runs, artifacts, spec).single()
    }

    fun clearLookup() {
        lookupTasker.cancel()
        _lookupState.value = DevBuildLookupState.Idle
    }

    fun install(commit: DevBuildCommit, context: ContextMP) {
        val artifact = commit.artifact ?: return
        launchInstall(DevBuildInstallTarget.of(commit), context) { target ->
            val token = getToken().trim()
            if (token.isEmpty()) throw TokenRequiredException()
            _installState.value = DevBuildInstallState.Downloading(target, 0, artifact.sizeInBytes)
            val downloadUrl = api.resolveArtifactDownloadUrl(token, artifact.archiveDownloadUrl)

            prepareSaveDir()
            val archive = saveDir.resolve("${artifact.name}-${commit.shortSha}.zip")
            api.downloadFile(downloadUrl, archive) { downloaded, total ->
                _installState.value = DevBuildInstallState.Downloading(target, downloaded, total ?: artifact.sizeInBytes)
            }

            _installState.value = DevBuildInstallState.Extracting(target)
            extractPackage(archive, saveDir.resolve(spec.packageFileName(commit.shortSha)))
        }
    }

    fun installPackage(url: String, fileName: String, context: ContextMP) {
        launchInstall(DevBuildInstallTarget.of(url, fileName), context) { target ->
            _installState.value = DevBuildInstallState.Downloading(target, 0, null)
            prepareSaveDir()
            val file = saveDir.resolve(fileName)
            api.downloadFile(url, file) { downloaded, total ->
                _installState.value = DevBuildInstallState.Downloading(target, downloaded, total)
            }

            file
        }
    }

    private fun launchInstall(
        target: DevBuildInstallTarget,
        context: ContextMP,
        preparePackage: suspend (DevBuildInstallTarget) -> SystemPath,
    ) {
        if (installTasker.isRunning.value) return
        installTasker.launch {
            try {
                val file = preparePackage(target)
                logger.info { "Dev build package ready: $file" }

                if (spec.kind.supportsAutomaticInstall) {
                    _installState.value = DevBuildInstallState.Installing(target)
                    val result = withContext(installDispatcher) {
                        installer.install(file, packageUrls = emptyList(), context = context)
                    }
                    _installState.value = when (result) {

                        InstallationResult.Succeed -> DevBuildInstallState.Idle
                        InstallationResult.RequiresInstallPermission -> DevBuildInstallState.Failed(
                            target,
                            DevBuildInstallFailure.Installer(
                                InstallationResult.Failed(
                                    InstallationFailureReason.INSTALL_PERMISSION_REQUEST_FAILED,
                                    "Installation permission is required",
                                ),
                            ),
                            file,
                        )
                        is InstallationResult.Failed -> DevBuildInstallState.Failed(
                            target,
                            DevBuildInstallFailure.Installer(result),
                            file,
                        )
                    }
                } else {
                    _installState.value = DevBuildInstallState.ReadyForManualInstall(target, file)
                }
            } catch (e: CancellationException) {
                _installState.value = DevBuildInstallState.Idle
                throw e
            } catch (e: TokenRequiredException) {
                _installState.value = DevBuildInstallState.Failed(target, DevBuildInstallFailure.TokenRequired, null)
            } catch (e: DevBuildPackageNotFoundException) {
                logger.error(e) { "Dev build artifact does not contain a package" }
                _installState.value = DevBuildInstallState.Failed(target, DevBuildInstallFailure.PackageNotFound, null)
            } catch (e: Throwable) {
                logger.error(e) { "Failed to install dev build ${target.label}" }
                _installState.value = DevBuildInstallState.Failed(target, DevBuildInstallFailure.Error(e), null)
            }
        }
    }

    private suspend fun prepareSaveDir() {
        withContext(Dispatchers.IO_) {
            saveDir.deleteRecursively()
            saveDir.createDirectories()
        }
    }

    private suspend fun extractPackage(archive: SystemPath, target: SystemPath): SystemPath {
        val kind = spec.kind
        if (!kind.extractsFromArchive) {
            withContext(Dispatchers.IO_) { archive.moveTo(target) }
            return target
        }
        val found = extractZipEntryByExtension(archive, kind.packageExtension, target)
        if (!found) {
            throw DevBuildPackageNotFoundException(archive.name, kind.packageExtension)
        }
        withContext(Dispatchers.IO_) { archive.delete() }

        return target
    }

    fun cancelInstall() {
        installTasker.cancel()
        _installState.update { state ->
            if (state is DevBuildInstallState.Busy) DevBuildInstallState.Idle else state
        }
    }

    fun dismissInstallResult() {
        _installState.update { state ->
            when (state) {
                is DevBuildInstallState.Failed, is DevBuildInstallState.ReadyForManualInstall -> DevBuildInstallState.Idle
                else -> state
            }
        }
    }

    suspend fun revealPackage(file: SystemPath, context: ContextMP): Boolean =
        installer.openForManualInstallation(file, context)

    @TestOnly
    suspend fun joinTasks() {
        refreshTasker.join()
        lookupTasker.join()
        installTasker.join()
    }

    private class TokenRequiredException : Exception()

    private companion object {
        val logger = logger<DevBuildsState>()
    }
}

@Stable
sealed interface DevBuildListState {

    @Immutable
    data object Idle : DevBuildListState

    @Immutable
    data class Loaded(val commits: List<DevBuildCommit>) : DevBuildListState

    @Immutable
    data class Failed(val throwable: Throwable) : DevBuildListState
}

@Stable
sealed interface DevBuildLookupState {
    @Immutable
    data object Idle : DevBuildLookupState

    @Immutable
    data object Loading : DevBuildLookupState

    @Immutable
    data class Resolved(val result: DevBuildLookupResult) : DevBuildLookupState

    @Immutable
    data class Failed(val failure: DevBuildLookupFailure) : DevBuildLookupState
}

@Stable
sealed interface DevBuildLookupResult {

    @Immutable
    data class Commit(
        val commit: DevBuildCommit,
        val pullRequest: DevBuildPullRequest? = null,
    ) : DevBuildLookupResult

    @Immutable
    data class Package(val url: String, val fileName: String) : DevBuildLookupResult
}

@Stable
sealed interface DevBuildLookupFailure {

    @Immutable
    data object Unrecognized : DevBuildLookupFailure

    @Immutable
    data class UnsupportedPackage(val fileName: String) : DevBuildLookupFailure

    @Immutable
    data class ArtifactNotForPlatform(val artifactName: String) : DevBuildLookupFailure

    @Immutable
    data class Error(val throwable: Throwable) : DevBuildLookupFailure
}

@Immutable
data class DevBuildInstallTarget(
    val key: String,
    val label: String,
    val title: String,
) {
    companion object {
        fun of(commit: DevBuildCommit) = DevBuildInstallTarget(commit.sha, commit.shortSha, commit.title)
        fun of(url: String, fileName: String) = DevBuildInstallTarget(url, fileName, url)
    }
}

@Stable
sealed interface DevBuildInstallState {
    @Immutable
    data object Idle : DevBuildInstallState

    sealed interface Busy : DevBuildInstallState {
        val target: DevBuildInstallTarget
    }

    @Immutable
    data class Downloading(
        override val target: DevBuildInstallTarget,
        val downloadedBytes: Long,

        val totalBytes: Long?,
    ) : Busy {

        val progress: Float?
            get() = totalBytes?.takeIf { it > 0 }?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
    }

    @Immutable
    data class Extracting(override val target: DevBuildInstallTarget) : Busy

    @Immutable
    data class Installing(override val target: DevBuildInstallTarget) : Busy

    @Immutable
    data class ReadyForManualInstall(
        val target: DevBuildInstallTarget,
        val file: SystemPath,
    ) : DevBuildInstallState

    @Immutable
    data class Failed(
        val target: DevBuildInstallTarget,
        val failure: DevBuildInstallFailure,
        val file: SystemPath?,
    ) : DevBuildInstallState
}

@Stable
sealed interface DevBuildInstallFailure {

    @Immutable
    data object TokenRequired : DevBuildInstallFailure

    @Immutable
    data object PackageNotFound : DevBuildInstallFailure

    @Immutable
    data class Installer(val result: InstallationResult.Failed) : DevBuildInstallFailure

    @Immutable
    data class Error(val throwable: Throwable) : DevBuildInstallFailure
}

class DevBuildPackageNotFoundException(
    archiveName: String,
    extension: String,
) : Exception("No .$extension file found in artifact archive $archiveName")
