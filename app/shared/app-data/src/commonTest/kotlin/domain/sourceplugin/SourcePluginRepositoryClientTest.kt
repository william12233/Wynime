package com.wynime.app.domain.sourceplugin

import kotlinx.coroutines.test.runTest
import com.wynime.source.plugin.api.SourceHttpClient
import com.wynime.source.plugin.api.SourceHttpRequest
import com.wynime.source.plugin.api.SourceHttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SourcePluginRepositoryClientTest {
    @Test
    fun `index accepts valid entries and sends cached etag`() = runTest {
        val first = validIndexResponse(etag = "\"one\"")
        val http = FakeSourceHttpClient(
            mapOf(
                "https://repo.example/index.json" to ArrayDeque(
                    listOf(
                        first,
                        SourceHttpResponse(
                            statusCode = 304,
                            finalUrl = "https://repo.example/index.json",
                        ),
                    ),
                ),
            ),
        )
        val client = SourcePluginRepositoryClient(http, baseUrl = "https://repo.example")

        val initial = client.fetchIndex()
        val cached = client.fetchIndex(
            SourcePluginRepositoryCache(etag = "\"one\"", index = initial),
        )

        assertEquals("demo", initial.plugins.single().id)
        assertTrue(cached.fromCache)
        assertEquals(initial, cached.index)
        assertEquals("\"one\"", cached.etag)
        assertEquals("\"one\"", http.requests[1].headers["If-None-Match"])
    }

    @Test
    fun `304 without cached index is rejected`() = runTest {
        val http = FakeSourceHttpClient(
            mapOf(
                "https://repo.example/index.json" to ArrayDeque(
                    listOf(SourceHttpResponse(304, "https://repo.example/index.json")),
                ),
            ),
        )
        val client = SourcePluginRepositoryClient(http, baseUrl = "https://repo.example")

        assertFailsWith<SourcePluginRepositoryException> {
            client.fetchIndex(SourcePluginRepositoryCache(etag = "\"cached\""))
        }
    }

    @Test
    fun `newer repository schema and duplicate ids are rejected`() = runTest {
        val newerSchema = FakeSourceHttpClient(
            mapOf(
                "https://repo.example/index.json" to ArrayDeque(
                    listOf(response("{\"schemaVersion\":3,\"pluginApiVersion\":3,\"plugins\":[]}")),
                ),
            ),
        )
        assertFailsWith<UnsupportedSourcePluginException> {
            SourcePluginRepositoryClient(newerSchema, baseUrl = "https://repo.example").fetchIndex()
        }

        val newerPluginApi = FakeSourceHttpClient(
            mapOf(
                "https://repo.example/index.json" to ArrayDeque(
                    listOf(response("{\"schemaVersion\":1,\"pluginApiVersion\":4,\"plugins\":[]}")),
                ),
            ),
        )
        assertFailsWith<UnsupportedSourcePluginException> {
            SourcePluginRepositoryClient(newerPluginApi, baseUrl = "https://repo.example").fetchIndex()
        }

        val duplicate = FakeSourceHttpClient(
            mapOf(
                "https://repo.example/index.json" to ArrayDeque(
                    listOf(
                        response(
                            """
                            {"schemaVersion":1,"pluginApiVersion":3,"plugins":[
                              {"id":"demo","name":"Demo","version":"1.0.0","website":"https://demo.example","platforms":["desktop"],"manifest":"manifests/demo.json"},
                              {"id":"demo","name":"Demo 2","version":"1.0.1","website":"https://demo.example","platforms":["desktop"],"manifest":"manifests/demo-2.json"}
                            ]}
                            """.trimIndent(),
                        ),
                    ),
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            SourcePluginRepositoryClient(duplicate, baseUrl = "https://repo.example").fetchIndex()
        }
    }

    @Test
    fun `historical manifest can be pinned to an immutable GitHub ref`() = runTest {
        val entry = validEntry(
            version = "1.0.1",
            manifest = "https://raw.githubusercontent.com/william12233/Wynime/v1.0.11/source/plugins/manifests/demo.json",
        )
        val manifestUrl = entry.manifest
        val http = FakeSourceHttpClient(
            mapOf(
                manifestUrl to ArrayDeque(
                    listOf(response(validManifestJson(version = "1.0.1"))),
                ),
                "https://raw.githubusercontent.com/william12233/Wynime/v1.0.11/source/plugins/artifacts/demo.jar" to
                    ArrayDeque(emptyList()),
            ),
        )

        val manifest = SourcePluginRepositoryClient(
            http,
            baseUrl = "https://raw.githubusercontent.com/william12233/Wynime/main/source/plugins",
        )
            .fetchManifest(entry)

        assertEquals("1.0.1", manifest.version)
        assertTrue(manifest.artifacts.getValue(com.wynime.source.plugin.api.SourcePluginPlatform.DESKTOP).url
            .startsWith("https://raw.githubusercontent.com"))
    }

    @Test
    fun `manifest rejects mismatched identity and outside artifact`() = runTest {
        val entry = validEntry()
        val mismatchedHttp = FakeSourceHttpClient(
            mapOf(
                "https://repo.example/manifests/demo.json" to ArrayDeque(
                    listOf(response(validManifestJson(id = "other"))),
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            SourcePluginRepositoryClient(mismatchedHttp, baseUrl = "https://repo.example").fetchManifest(entry)
        }

        val outsideHttp = FakeSourceHttpClient(
            mapOf(
                "https://repo.example/manifests/demo.json" to ArrayDeque(
                    listOf(response(validManifestJson(artifactUrl = "https://evil.example/demo.jar"))),
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            SourcePluginRepositoryClient(outsideHttp, baseUrl = "https://repo.example").fetchManifest(entry)
        }
    }

    @Test
    fun `manifest rejects non https icon`() = runTest {
        val entry = validEntry()
        val http = FakeSourceHttpClient(
            mapOf(
                "https://repo.example/manifests/demo.json" to ArrayDeque(
                    listOf(response(validManifestJson(icon = "http://icons.example/demo.png"))),
                ),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            SourcePluginRepositoryClient(http, baseUrl = "https://repo.example").fetchManifest(entry)
        }
    }

    @Test
    fun `absolute repository url must stay under configured repository path`() = runTest {
        val http = FakeSourceHttpClient(
            mapOf(
                "https://repo.example/first-party/manifests/demo.json" to ArrayDeque(
                    listOf(
                        response(
                            validManifestJson(artifactUrl = "https://repo.example/other/demo.jar"),
                        ),
                    ),
                ),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            SourcePluginRepositoryClient(
                http,
                baseUrl = "https://repo.example/first-party",
            ).fetchManifest(validEntry(manifest = "manifests/demo.json"))
        }
    }

    @Test
    fun `repository paths and artifact hashes are validated`() = runTest {
        val http = FakeSourceHttpClient(emptyMap())
        val client = SourcePluginRepositoryClient(http, baseUrl = "https://repo.example")
        val unsafeEntry = validEntry(manifest = "../escape.json")

        assertFailsWith<IllegalArgumentException> { client.fetchManifest(unsafeEntry) }
        assertFailsWith<IllegalArgumentException> {
            client.downloadArtifact(SourcePluginArtifact("artifacts/demo.jar", "not-a-sha"))
        }
    }

    private fun validIndexResponse(etag: String? = null): SourceHttpResponse {
        return response(
            """
            {"schemaVersion":1,"pluginApiVersion":3,"plugins":[
              {"id":"demo","name":"Demo","version":"1.0.0","website":"https://demo.example","platforms":["desktop"],"manifest":"manifests/demo.json"}
            ]}
            """.trimIndent(),
            headers = etag?.let { mapOf("ETag" to it) }.orEmpty(),
        )
    }

    private fun validEntry(
        id: String = "demo",
        version: String = "1.0.0",
        manifest: String = "manifests/demo.json",
    ) = SourcePluginIndexEntry(
        id = id,
        displayName = "Demo",
        version = version,
        website = "https://demo.example",
        platforms = setOf(com.wynime.source.plugin.api.SourcePluginPlatform.DESKTOP),
        manifest = manifest,
    )

    private fun validManifestJson(
        id: String = "demo",
        version: String = "1.0.0",
        artifactUrl: String = "artifacts/demo.jar",
        icon: String? = null,
    ): String =
        """
        {"id":"$id","name":"Demo","version":"$version","pluginApiVersion":3,
         "minHostVersion":"1.0.0","entryClass":"demo.Entry","website":"https://demo.example",
         ${icon?.let { "\"icon\":\"$it\"," } ?: ""}
         "platforms":["desktop"],"artifacts":{"desktop":{"url":"$artifactUrl","sha256":"${"0".repeat(64)}","format":"jar"}}}
        """.trimIndent().replace("\n", "")

    private fun response(body: String, headers: Map<String, String> = emptyMap()) = SourceHttpResponse(
        statusCode = 200,
        finalUrl = "https://repo.example/document",
        headers = headers,
        body = body.encodeToByteArray(),
    )

    private class FakeSourceHttpClient(
        responses: Map<String, ArrayDeque<SourceHttpResponse>>,
    ) : SourceHttpClient {
        private val responses = responses.toMutableMap()
        val requests = mutableListOf<SourceHttpRequest>()

        override suspend fun execute(request: SourceHttpRequest): SourceHttpResponse {
            requests += request
            return responses[request.url]?.removeFirstOrNull()
                ?: error("No fake response for ${request.url}")
        }
    }
}
