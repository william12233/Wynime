package com.wynime.app.ui.update.devbuild

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import com.wynime.app.tools.update.InstallationFailureReason
import com.wynime.app.tools.update.InstallationResult
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.SystemPaths
import com.wynime.utils.io.createTempDirectory
import com.wynime.utils.io.deleteRecursively
import com.wynime.utils.io.list
import com.wynime.utils.io.name
import com.wynime.utils.io.readBytes
import com.wynime.utils.io.resolve
import com.wynime.utils.io.toFile
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(TestOnly::class)
class DevBuildsStateTest {
    private val windowsSpec = DevBuildPackageSpec(listOf("wynime-windows-portable"), DevBuildPackageKind.WINDOWS_PORTABLE_ZIP)
    private val androidSpec = DevBuildPackageSpec(
        listOf("wynime-android-arm64-v8a-release", "wynime-android-universal-release"),
        DevBuildPackageKind.ANDROID_APK,
        debugArtifactNames = listOf("wynime-android-arm64-v8a-debug", "wynime-android-universal-debug"),
    )

    private fun TestScope.createState(
        client: HttpClient,
        spec: DevBuildPackageSpec,
        installer: FakeInstaller,
        saveDir: SystemPath,
        token: String = "token",
        currentVersionName: String = "4.12.0-main-aaaaaaaa",
    ) = DevBuildsState(
        api = GitHubDevBuildApi(client),
        spec = spec,
        installer = installer,
        saveDir = saveDir,
        getToken = { token },
        currentVersionName = currentVersionName,
        backgroundScope = backgroundScope,
        installDispatcher = Dispatchers.Unconfined,
    )

    private inline fun withTempDir(block: (SystemPath) -> Unit) {
        val dir = SystemPaths.createTempDirectory("dev-builds-state-test")
        try {
            block(dir.resolve("dev-builds"))
        } finally {
            dir.deleteRecursively()
        }
    }

    private suspend fun DevBuildsState.loadCommits(): List<DevBuildCommit> {
        refresh()
        joinTasks()
        return assertIs<DevBuildListState.Loaded>(listState.value).commits
    }

    private suspend fun DevBuildsState.lookupAndJoin(text: String): DevBuildLookupState {
        lookup(text)
        joinTasks()
        return lookupState.value
    }

    private suspend fun DevBuildsState.lookupCommit(text: String): DevBuildLookupResult.Commit =
        assertIs<DevBuildLookupResult.Commit>(assertIs<DevBuildLookupState.Resolved>(lookupAndJoin(text)).result)

    private suspend fun DevBuildsState.lookupFailure(text: String): DevBuildLookupFailure =
        assertIs<DevBuildLookupState.Failed>(lookupAndJoin(text)).failure

    @Test
    fun `refresh merges commits, build status and the platform artifact`() = runTest {
        withTempDir { saveDir ->
            val client = fullGitHubMockClient("wynime-android-arm64-v8a-release", zipBytes())
            val state = createState(client, androidSpec, FakeInstaller(), saveDir)
            assertEquals(DevBuildListState.Idle, state.listState.value)

            val commits = state.loadCommits()
            assertEquals(listOf(SHA_A, SHA_B), commits.map { it.sha })
            assertEquals(DevBuildStatus.SUCCESS, commits[0].build?.status)
            assertEquals(10, commits[0].artifact?.id)
            assertEquals(DevBuildStatus.IN_PROGRESS, commits[1].build?.status)
            assertNull(commits[1].artifact)

            assertEquals("aaaaaaaa", state.currentCommitShortSha)
            assertTrue(state.isCurrentCommit(commits[0]))
            assertFalse(state.isCurrentCommit(commits[1]))
            assertFalse(state.isRefreshing.value)
        }
    }

    @Test
    fun `refresh failure is exposed and a later refresh recovers`() = runTest {
        withTempDir { saveDir ->
            var fail = true
            val healthy = fullGitHubMockHandler("wynime-android-arm64-v8a-release", zipBytes())
            val client = gitHubMockClient { request ->
                if (fail) {
                    respond(
                        """{"message": "rate limited"}""",
                        HttpStatusCode.Forbidden,
                        headersOf("x-ratelimit-remaining", "0"),
                    )
                } else {
                    healthy(request)
                }
            }
            val state = createState(client, androidSpec, FakeInstaller(), saveDir)
            state.refresh()
            state.joinTasks()
            val failed = assertIs<DevBuildListState.Failed>(state.listState.value)
            val e = assertIs<GitHubApiException>(failed.throwable)
            assertTrue(e.isRateLimited)

            fail = false
            assertEquals(2, state.loadCommits().size)
        }
    }

    @Test
    fun `install requires a github token`() = runTest {
        withTempDir { saveDir ->
            val installer = FakeInstaller()
            val client = fullGitHubMockClient("wynime-android-arm64-v8a-release", zipBytes())
            val state = createState(client, androidSpec, installer, saveDir, token = " ")
            val commit = state.loadCommits()[0]

            state.install(commit, testContext)
            state.joinTasks()

            val failed = assertIs<DevBuildInstallState.Failed>(state.installState.value)
            assertEquals(DevBuildInstallFailure.TokenRequired, failed.failure)
            assertNull(failed.file)
            assertTrue(installer.installed.isEmpty())

            state.dismissInstallResult()
            assertEquals(DevBuildInstallState.Idle, state.installState.value)
        }
    }

    @Test
    fun `install ignores commits without a package`() = runTest {
        withTempDir { saveDir ->
            val installer = FakeInstaller()
            val state = createState(fullGitHubMockClient("wynime-android-arm64-v8a-release", zipBytes()), androidSpec, installer, saveDir)
            val commit = state.loadCommits()[1]
            state.install(commit, testContext)
            state.joinTasks()
            assertEquals(DevBuildInstallState.Idle, state.installState.value)
            assertTrue(installer.installed.isEmpty())
        }
    }

    @Test
    fun `install downloads the artifact, extracts the apk and calls the installer`() = runTest {
        withTempDir { saveDir ->
            val apk = ByteArray(2048) { (it % 7).toByte() }
            val installer = FakeInstaller()
            val client = fullGitHubMockClient("wynime-android-arm64-v8a-release", zipBytes("Wynime-4.12.0.apk" to apk))
            val state = createState(client, androidSpec, installer, saveDir)
            val commit = state.loadCommits()[0]

            state.install(commit, testContext)
            state.joinTasks()

            assertEquals(DevBuildInstallState.Idle, state.installState.value)
            val installed = installer.installed.single()
            assertEquals(saveDir.resolve("wynime-main-aaaaaaaa.apk"), installed)
            assertContentEquals(apk, installed.readBytes())

            assertEquals(listOf("wynime-main-aaaaaaaa.apk"), saveDir.list().map { it.name })
        }
    }

    @Test
    fun `windows artifact zip is handed to the installer as the package`() = runTest {
        withTempDir { saveDir ->
            val archive = zipBytes("Wynime/Wynime.exe" to byteArrayOf(1, 2), "Wynime/app/x.jar" to byteArrayOf(3))
            val installer = FakeInstaller()
            val client = fullGitHubMockClient("wynime-windows-portable", archive)
            val state = createState(client, windowsSpec, installer, saveDir)
            val commit = state.loadCommits()[0]

            state.install(commit, testContext)
            state.joinTasks()

            val installed = installer.installed.single()
            assertEquals(saveDir.resolve("wynime-main-aaaaaaaa.zip"), installed)
            assertContentEquals(archive, installed.readBytes())
        }
    }

    @Test
    fun `missing package in the artifact is reported`() = runTest {
        withTempDir { saveDir ->
            val installer = FakeInstaller()
            val client = fullGitHubMockClient("wynime-android-arm64-v8a-release", zipBytes("readme.txt" to byteArrayOf(1)))
            val state = createState(client, androidSpec, installer, saveDir)
            val commit = state.loadCommits()[0]

            state.install(commit, testContext)
            state.joinTasks()

            val failed = assertIs<DevBuildInstallState.Failed>(state.installState.value)
            assertEquals(DevBuildInstallFailure.PackageNotFound, failed.failure)
            assertTrue(installer.installed.isEmpty())
        }
    }

    @Test
    fun `installer failure keeps the package for manual installation`() = runTest {
        withTempDir { saveDir ->
            val result = InstallationResult.Failed(InstallationFailureReason.FAILED_TO_MOUNT_DMG, "boom")
            val installer = FakeInstaller(result)
            val client = fullGitHubMockClient("wynime-android-arm64-v8a-release", zipBytes("wynime.apk" to byteArrayOf(1)))
            val state = createState(client, androidSpec, installer, saveDir)
            val commit = state.loadCommits()[0]

            state.install(commit, testContext)
            state.joinTasks()

            val failed = assertIs<DevBuildInstallState.Failed>(state.installState.value)
            assertEquals(DevBuildInstallFailure.Installer(result), failed.failure)
            assertEquals(saveDir.resolve("wynime-main-aaaaaaaa.apk"), failed.file)
        }
    }

    @Test
    fun `network failure during download is reported without a file`() = runTest {
        withTempDir { saveDir ->
            val installer = FakeInstaller()
            val client = fullGitHubMockClient("wynime-android-arm64-v8a-release", zipBytes(), expectedToken = "other")
            val state = createState(client, androidSpec, installer, saveDir)
            val commit = state.loadCommits()[0]

            state.install(commit, testContext)
            state.joinTasks()

            val failed = assertIs<DevBuildInstallState.Failed>(state.installState.value)
            val error = assertIs<DevBuildInstallFailure.Error>(failed.failure)
            assertTrue(assertIs<GitHubApiException>(error.throwable).isUnauthorized)
            assertNull(failed.file)
        }
    }

    @Test
    fun `lookup resolves a commit link to its latest build and artifact`() = runTest {
        withTempDir { saveDir ->
            val client = fullGitHubMockClient("wynime-android-arm64-v8a-release", zipBytes())
            val state = createState(client, androidSpec, FakeInstaller(), saveDir)
            assertEquals(DevBuildLookupState.Idle, state.lookupState.value)

            val result = state.lookupCommit("https://github.com/william12233/Wynime/commit/$SHA_A")
            assertNull(result.pullRequest)
            assertEquals(SHA_A, result.commit.sha)
            assertEquals("feat(update): first line", result.commit.title)
            assertEquals(200, result.commit.build?.id)
            assertEquals(DevBuildStatus.SUCCESS, result.commit.build?.status)
            assertEquals(10, result.commit.artifact?.id)

            val second = state.lookupCommit(SHA_B.take(8))
            assertEquals(SHA_B, second.commit.sha)
            assertEquals(DevBuildStatus.IN_PROGRESS, second.commit.build?.status)
            assertNull(second.commit.artifact)

            state.clearLookup()
            assertEquals(DevBuildLookupState.Idle, state.lookupState.value)
        }
    }

    @Test
    fun `lookup resolves a pull request to its head commit`() = runTest {
        withTempDir { saveDir ->
            val client = fullGitHubMockClient("wynime-android-arm64-v8a-release", zipBytes())
            val state = createState(client, androidSpec, FakeInstaller(), saveDir)

            val result = state.lookupCommit("https://github.com/william12233/Wynime/pull/42/files")
            val pr = assertNotNull(result.pullRequest)
            assertEquals(42, pr.number)
            assertEquals("feat: pr title", pr.title)
            assertEquals("feat/x", pr.headRef)
            assertFalse(pr.isFromFork)
            assertEquals(SHA_A, result.commit.sha)
            assertEquals(10, result.commit.artifact?.id)

            val notFound = assertIs<DevBuildLookupFailure.Error>(state.lookupFailure("#43"))
            assertTrue(assertIs<GitHubApiException>(notFound.throwable).isNotFound)
        }
    }

    @Test
    fun `fork pull request without a release apk falls back to the debug apk`() = runTest {
        withTempDir { saveDir ->
            val forkSha = "cccccccc33333333cccccccc33333333cccccccc"
            val client = gitHubMockClient { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/pulls/7") -> respondJson(pullRequestJson(7, forkSha, headRepo = "someone/animeko"))
                    path.endsWith("/commits/$forkSha") -> respondJson(
                        """{"sha": "$forkSha", "html_url": "", "commit": {"message": "fork change"}}""",
                    )

                    path.endsWith("/build.yml/runs") -> {
                        assertEquals(forkSha, request.url.parameters["head_sha"])
                        respondJson(
                            """{"workflow_runs": [
                              {"id": 500, "head_sha": "$forkSha", "status": "completed", "conclusion": "success", "html_url": ""}
                            ]}""",
                        )
                    }

                    path.endsWith("/actions/runs/500/artifacts") -> respondJson(
                        """{"artifacts": [
                          {"id": 51, "name": "wynime-android-universal-debug", "size_in_bytes": 2, "archive_download_url": "u51", "expired": false,
                           "workflow_run": {"id": 500, "head_sha": "$forkSha"}},
                          {"id": 52, "name": "wynime-android-arm64-v8a-debug", "size_in_bytes": 3, "archive_download_url": "u52", "expired": false,
                           "workflow_run": {"id": 500, "head_sha": "$forkSha"}},
                          {"id": 53, "name": "wynime-windows-portable", "size_in_bytes": 4, "archive_download_url": "u53", "expired": false,
                           "workflow_run": {"id": 500, "head_sha": "$forkSha"}}
                        ]}""",
                    )

                    else -> error("Unexpected request: ${request.url}")
                }
            }
            val state = createState(client, androidSpec, FakeInstaller(), saveDir)

            val result = state.lookupCommit("https://github.com/william12233/Wynime/pull/7")
            assertTrue(assertNotNull(result.pullRequest).isFromFork)
            val artifact = assertNotNull(result.commit.artifact)
            assertEquals("wynime-android-arm64-v8a-debug", artifact.name)
            assertTrue(state.spec.isDebugArtifact(artifact.name))
        }
    }

    @Test
    fun `lookup resolves workflow run and artifact links`() = runTest {
        withTempDir { saveDir ->
            val client = fullGitHubMockClient("wynime-android-arm64-v8a-release", zipBytes())
            val state = createState(client, androidSpec, FakeInstaller(), saveDir)

            val run = state.lookupCommit("https://github.com/william12233/Wynime/actions/runs/100/job/1")
            assertEquals(SHA_A, run.commit.sha)
            assertEquals(100, run.commit.build?.id)
            assertEquals(DevBuildStatus.FAILURE, run.commit.build?.status)
            assertNull(run.commit.artifact, "run 100 uploaded nothing")

            val artifact = state.lookupCommit("https://github.com/william12233/Wynime/actions/runs/200/artifacts/10")
            assertEquals(SHA_A, artifact.commit.sha)
            assertEquals(200, artifact.commit.build?.id)
            assertEquals(10, artifact.commit.artifact?.id)

            val windows = createState(client, windowsSpec, FakeInstaller(), saveDir)
            val mismatch = assertIs<DevBuildLookupFailure.ArtifactNotForPlatform>(
                windows.lookupFailure("https://api.github.com/repos/william12233/Wynime/actions/artifacts/10/zip"),
            )
            assertEquals("wynime-android-arm64-v8a-release", mismatch.artifactName)
        }
    }

    @Test
    fun `package links are accepted only with the platform extension`() = runTest {
        withTempDir { saveDir ->
            val client = gitHubMockClient { error("no requests expected: ${it.url}") }
            val state = createState(client, androidSpec, FakeInstaller(), saveDir)

            val url = "https://github.com/william12233/Wynime/releases/download/v4.12.0/wynime-4.12.0-arm64-v8a.apk"
            val resolved = assertIs<DevBuildLookupState.Resolved>(state.lookupAndJoin(url))
            assertEquals(DevBuildLookupResult.Package(url, "wynime-4.12.0-arm64-v8a.apk"), resolved.result)

            val unsupported = assertIs<DevBuildLookupFailure.UnsupportedPackage>(
                state.lookupFailure("https://example.com/wynime-windows.zip"),
            )
            assertEquals("wynime-windows.zip", unsupported.fileName)
            assertEquals(DevBuildLookupFailure.Unrecognized, state.lookupFailure("what is this"))
            assertEquals(
                DevBuildLookupFailure.Unrecognized,
                state.lookupFailure("https://github.com/other/repo/commit/$SHA_A"),
            )
        }
    }

    @Test
    fun `installPackage downloads the package following redirects without a token`() = runTest {
        withTempDir { saveDir ->
            val apk = ByteArray(1024) { (it % 5).toByte() }
            val installer = FakeInstaller()
            val requests = mutableListOf<HttpRequestData>()
            val client = gitHubMockClient { request ->
                requests += request
                when (request.url.host) {
                    "github.com" -> respond(
                        "",
                        HttpStatusCode.Found,
                        headersOf(HttpHeaders.Location, "https://objects.example.com/blob?sig=1"),
                    )

                    "objects.example.com" -> respond(
                        apk,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentLength, apk.size.toString()),
                    )

                    else -> error("Unexpected request: ${request.url}")
                }
            }
            val state = createState(client, androidSpec, installer, saveDir, token = "")
            val url = "https://github.com/william12233/Wynime/releases/download/v4.12.0/wynime-4.12.0-arm64-v8a.apk"

            state.installPackage(url, "wynime-4.12.0-arm64-v8a.apk", testContext)
            state.joinTasks()

            assertEquals(DevBuildInstallState.Idle, state.installState.value)
            val installed = installer.installed.single()
            assertEquals(saveDir.resolve("wynime-4.12.0-arm64-v8a.apk"), installed)
            assertContentEquals(apk, installed.readBytes())
            assertEquals(listOf("github.com", "objects.example.com"), requests.map { it.url.host })
            assertTrue(requests.all { it.headers[HttpHeaders.Authorization] == null })
        }
    }

    @Test
    fun `installPackage failure reports the target`() = runTest {
        withTempDir { saveDir ->
            val installer = FakeInstaller()
            val client = gitHubMockClient { respondJson("""{"message": "gone"}""", HttpStatusCode.NotFound) }
            val state = createState(client, androidSpec, installer, saveDir)
            val url = "https://example.com/ani.apk"

            state.installPackage(url, "wynime.apk", testContext)
            state.joinTasks()

            val failed = assertIs<DevBuildInstallState.Failed>(state.installState.value)
            assertEquals(DevBuildInstallTarget(url, "wynime.apk", url), failed.target)
            assertIs<DevBuildInstallFailure.Error>(failed.failure)
            assertTrue(installer.installed.isEmpty())
        }
    }
}
