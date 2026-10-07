package com.wynime.app.tools.update

import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.toKtPath
import com.wynime.utils.ktor.asScopedHttpClient
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class DefaultFileDownloaderTest {

    private val fileContent = "Hello, this is a test file!"

    private val corruptedFileContent = "Corrupted content - not matching the checksum"

    @Test
    fun `test successful download`() = testApplication {
        setupRouting()

        val downloader = DefaultFileDownloader(
            createClient {
                expectSuccess = true
                install(HttpTimeout)
            }.asScopedHttpClient(),
        )
        val tempDir = createTempDirectory(prefix = "file-downloader-test").toFile()

        val targetFile = File(tempDir, "test-file.txt").apply { delete() }

        val succeeded = downloader.download(
            alternativeUrls = listOf("/file"),
            filenameProvider = { "test-file.txt" },
            saveDir = tempDir.toKtPath().inSystem,
        )

        assertNotNull(succeeded, "Download method should return true for a fresh download.")

        val finalState = downloader.state.first { it is FileDownloaderState.Completed }
        when (finalState) {
            is FileDownloaderState.Succeed -> {
                assertTrue(targetFile.exists(), "File must exist after successful download.")

                assertEquals(fileContent, targetFile.readText(), "Downloaded file contents mismatch.")
                assertEquals(true, finalState.checked)
            }

            else -> fail("Expected Succeed but got $finalState")
        }

        targetFile.delete()
        tempDir.deleteRecursively()
    }

    @Test
    fun `test no checksum`() = testApplication {
        routing {
            get("/file") {
                call.respondText(fileContent, ContentType.Text.Plain)
            }
        }

        val downloader = DefaultFileDownloader(
            createClient {
                expectSuccess = true
                install(HttpTimeout)
            }.asScopedHttpClient(),
        )
        val tempDir = createTempDirectory(prefix = "file-downloader-test").toFile()

        val targetFile = File(tempDir, "test-file.txt").apply { delete() }

        val succeeded = downloader.download(
            alternativeUrls = listOf("/file"),
            filenameProvider = { "test-file.txt" },
            saveDir = tempDir.toKtPath().inSystem,
        )

        assertNotNull(succeeded, "Download method should return true for a fresh download.")

        val finalState = downloader.state.first { it is FileDownloaderState.Completed }
        when (finalState) {
            is FileDownloaderState.Succeed -> {
                assertTrue(targetFile.exists(), "File must exist after successful download.")

                assertEquals(fileContent, targetFile.readText(), "Downloaded file contents mismatch.")
                assertEquals(false, finalState.checked)
            }

            else -> fail("Expected Succeed but got $finalState")
        }

        targetFile.delete()
        tempDir.deleteRecursively()
    }

    private fun ApplicationTestBuilder.setupRouting() {
        routing {

            get("/file") {
                call.respondText(fileContent, ContentType.Text.Plain)
            }
            get("/file.sha1") {

                call.respondText("0fc7c10d0d0193c654b2654dba75c319bcdc6edb\n")
            }

            get("/corrupted-file") {
                call.respondText(corruptedFileContent, ContentType.Text.Plain)
            }
            get("/corrupted-file.sha1") {

                call.respondText("ffffffffffffffffffffffffffffffffffffffff")
            }

            get("/unavailable") {
                call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
            }
        }
    }

    @Test
    fun `test skip download if file already exists and matches checksum`() = testApplication {
        setupRouting()

        val downloader = DefaultFileDownloader(
            createClient {
                expectSuccess = true
                install(HttpTimeout)
            }.asScopedHttpClient(),
        )
        val tempDir = createTempDirectory(prefix = "file-downloader-test").toFile()

        val targetFile = File(tempDir, "test-file.txt").apply {
            writeText(fileContent)
        }

        val succeeded = downloader.download(
            alternativeUrls = listOf("/file"),
            filenameProvider = { "test-file.txt" },
            saveDir = tempDir.toKtPath().inSystem,
        )

        assertNotNull(succeeded, "Should skip download for correct existing file.")

        val finalState = downloader.state.first { it is FileDownloaderState.Completed }
        assertTrue(finalState is FileDownloaderState.Succeed)

        assertEquals(fileContent, targetFile.readText(), "File content changed unexpectedly.")

        targetFile.delete()
        tempDir.deleteRecursively()
    }

    @Test
    fun `test re-download if local file is corrupted`() = testApplication {
        setupRouting()

        val downloader = DefaultFileDownloader(
            createClient {
                expectSuccess = true
                install(HttpTimeout)
            }.asScopedHttpClient(),
        )
        val tempDir = createTempDirectory(prefix = "file-downloader-test").toFile()
        val targetFile = File(tempDir, "test-file.txt").apply {

            writeText("Corrupted local content")
        }

        val succeeded = downloader.download(
            alternativeUrls = listOf("/file"),
            filenameProvider = { "test-file.txt" },
            saveDir = tempDir.toKtPath().inSystem,
        )
        assertNotNull(succeeded, "Download should succeed after re-downloading.")

        val finalState = downloader.state.first { it is FileDownloaderState.Completed }
        when (finalState) {
            is FileDownloaderState.Succeed -> {
                assertEquals(fileContent, targetFile.readText(), "File didn't get replaced with correct content.")
            }

            else -> fail("Expected Succeed but got $finalState")
        }

        targetFile.delete()
        tempDir.deleteRecursively()
    }

    @Test
    fun `test server corrupted file fails checksum`() = testApplication {
        setupRouting()
        val downloader = DefaultFileDownloader(
            createClient {
                expectSuccess = true
                install(HttpTimeout)
            }.asScopedHttpClient(),
        )
        val tempDir = createTempDirectory(prefix = "file-downloader-test").toFile()
        val targetFile = File(tempDir, "corrupted-file.txt")

        val succeeded = downloader.download(
            alternativeUrls = listOf("/corrupted-file"),
            filenameProvider = { "corrupted-file.txt" },
            saveDir = tempDir.toKtPath().inSystem,
        )

        assertNull(succeeded, "Should fail because the file's checksum won't match.")

        val finalState = downloader.state.first { it is FileDownloaderState.Completed }
        assertTrue(finalState is FileDownloaderState.Failed, "Expected final state to be Failed.")

        assertTrue(!targetFile.exists(), "File should be deleted after checksum mismatch.")

        targetFile.delete()
        tempDir.deleteRecursively()
    }

    @Test
    fun `progress reporter does not outlive download attempt`() = testApplication {
        setupRouting()
        val client = createClient {
            expectSuccess = true
            install(HttpTimeout)
        }
        val downloader = DefaultFileDownloader(client.asScopedHttpClient())
        val tempDir = createTempDirectory(prefix = "file-downloader-test").toFile()

        val succeeded = downloader.download(
            alternativeUrls = listOf("/file"),
            filenameProvider = { "test-file-reporter.txt" },
            saveDir = tempDir.toKtPath().inSystem,
        )
        assertNotNull(succeeded)

        assertEquals(
            emptyList(),
            client.coroutineContext.job.children.filter { it.isActive }.toList(),
            "No coroutine should be left running in the HttpClient scope after download.",
        )

        tempDir.deleteRecursively()
    }

    @Test
    fun `test progress updates`() = testApplication {
        setupRouting()
        val downloader = DefaultFileDownloader(
            createClient {
                expectSuccess = true
                install(HttpTimeout)
            }.asScopedHttpClient(),
        )
        val tempDir = createTempDirectory(prefix = "file-downloader-test").toFile()
        val targetFile = File(tempDir, "test-file-progress.txt").apply { delete() }

        val progressValues = mutableListOf<Float>()

        coroutineScope {
            val job = launch {
                downloader.progress
                    .takeWhile { it < 1.0f }
                    .collect { progress ->
                        println(progress)
                        progressValues.add(progress)
                    }
            }

            val succeeded = downloader.download(
                alternativeUrls = listOf("/file"),
                filenameProvider = { "test-file-progress.txt" },
                saveDir = tempDir.toKtPath().inSystem,
            )

            val finalState = downloader.state.first { it is FileDownloaderState.Completed }
            job.join()

            assertNotNull(succeeded, "Progress test: download should succeed.")

            when (finalState) {
                is FileDownloaderState.Succeed -> {
                    assertTrue(progressValues.isNotEmpty(), "We should have collected some progress updates.")

                }

                else -> fail("Expected Succeed but got $finalState")
            }

            targetFile.delete()
            tempDir.deleteRecursively()
        }
    }
}
