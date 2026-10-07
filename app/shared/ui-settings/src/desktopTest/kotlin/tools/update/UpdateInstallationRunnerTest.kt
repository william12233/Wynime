package com.wynime.app.tools.update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import com.wynime.app.platform.Context
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.inSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UpdateInstallationRunnerTest {
    @Test
    fun `tracks installation failure and dismisses it`() = runTest {
        val installationStarted = CompletableDeferred<Unit>()
        val finishInstallation = CompletableDeferred<Unit>()
        val failure = InstallationResult.Failed(InstallationFailureReason.FAILED_TO_COPY, "failed")
        val runner = UpdateInstallationRunner(
            object : UpdateInstaller {
                override fun install(file: SystemPath, context: Context): InstallationResult = failure

                override suspend fun install(
                    file: SystemPath,
                    packageUrls: List<String>,
                    context: Context,
                ): InstallationResult {
                    installationStarted.complete(Unit)
                    finishInstallation.await()
                    return failure
                }
            },
        )

        val installation = async {
            runner.install(Path("update").inSystem, emptyList(), object : Context() {})
        }
        installationStarted.await()
        assertEquals(UpdateInstallationState.Installing, runner.state.value)

        finishInstallation.complete(Unit)
        installation.await()
        assertEquals(failure, assertIs<UpdateInstallationState.Failed>(runner.state.value).result)

        runner.dismissFailure()
        assertEquals(UpdateInstallationState.Idle, runner.state.value)
    }

    @Test
    fun `permission request is waiting and never reported as success`() = runTest {
        val runner = UpdateInstallationRunner(
            object : UpdateInstaller {
                override fun install(file: SystemPath, context: Context): InstallationResult =
                    InstallationResult.RequiresInstallPermission

                override suspend fun install(
                    file: SystemPath,
                    packageUrls: List<String>,
                    context: Context,
                ): InstallationResult = InstallationResult.RequiresInstallPermission
            },
        )

        runner.install(Path("update.apk").inSystem, emptyList(), object : Context() {})

        assertEquals(UpdateInstallationState.WaitingForPermission, runner.state.value)
        runner.returnToDownloaded()
        assertEquals(UpdateInstallationState.Idle, runner.state.value)
    }

    @Test
    fun `installer exception becomes failed`() = runTest {
        val runner = UpdateInstallationRunner(
            object : UpdateInstaller {
                override fun install(file: SystemPath, context: Context): InstallationResult = error("boom")

                override suspend fun install(
                    file: SystemPath,
                    packageUrls: List<String>,
                    context: Context,
                ): InstallationResult = error("boom")
            },
        )

        runner.install(Path("update.apk").inSystem, emptyList(), object : Context() {})

        val state = assertIs<UpdateInstallationState.Failed>(runner.state.value)
        assertEquals(InstallationFailureReason.FAILED_TO_COPY, state.result.reason)
        assertEquals("boom", state.result.message)
    }
}
