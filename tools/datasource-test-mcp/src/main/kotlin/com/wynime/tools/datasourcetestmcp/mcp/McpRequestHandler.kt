package com.wynime.tools.datasourcetestmcp.mcp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

@Serializable
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

class McpToolRegistration(
    val tool: McpTool,
    val handler: suspend (arguments: JsonElement) -> JsonElement,
)

class McpRequestHandler(
    private val registrations: List<McpToolRegistration>,
    private val json: Json,
) {

    val protocolJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val toolCallMutex = Mutex()

    suspend fun handle(request: RpcRequest): RpcResponse? {
        val id = request.id
        return when (request.method) {
            "initialize" -> id?.let { RpcResponse(id = it, result = initializeResult(request.params)) }
            "notifications/initialized" -> null
            "ping" -> id?.let { RpcResponse(id = it, result = buildJsonObject {}) }
            "tools/list" -> id?.let { RpcResponse(id = it, result = toolsListResult()) }
            "tools/call" -> handleToolsCall(request)
            else -> id?.let { RpcResponse(id = it, error = RpcError(-32601, "Method not found: ${request.method}")) }
        }
    }

    private suspend fun handleToolsCall(request: RpcRequest): RpcResponse? {
        val id = request.id
        val params = request.params as? JsonObject ?: JsonObject(emptyMap())
        val name = (params["name"] as? JsonPrimitive)?.contentOrNull
            ?: return id?.let { RpcResponse(id = it, error = RpcError(-32602, "Missing tool name")) }
        val registration = registrations.find { it.tool.name == name }
            ?: return id?.let { RpcResponse(id = it, error = RpcError(-32602, "Unknown tool: $name")) }
        val arguments = params["arguments"] ?: JsonObject(emptyMap())

        val structured = try {
            toolCallMutex.withLock { registration.handler(arguments) }
        } catch (exception: TimeoutCancellationException) {

            currentCoroutineContext().ensureActive()
            return id?.let { RpcResponse(id = it, result = toolCallResult(errorResultOf(exception), isError = true)) }
        } catch (exception: CancellationException) {

            throw exception
        } catch (exception: Throwable) {
            return id?.let { RpcResponse(id = it, result = toolCallResult(errorResultOf(exception), isError = true)) }
        }
        return id?.let { RpcResponse(id = it, result = toolCallResult(structured, isError = false)) }
    }

    private fun toolsListResult(): JsonObject {
        return buildJsonObject {
            put(
                "tools",
                protocolJson.encodeToJsonElement(
                    ListSerializer(McpTool.serializer()),
                    registrations.map { it.tool },
                ),
            )
        }
    }

    private fun errorResultOf(exception: Throwable): JsonObject {
        return buildJsonObject {
            put("ok", false)
            put("summary", "${exception::class.simpleName}: ${exception.message.orEmpty()}")
        }
    }

    private fun toolCallResult(structured: JsonElement, isError: Boolean): JsonObject {
        return buildJsonObject {
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", json.encodeToString(JsonElement.serializer(), structured))
                        },
                    )
                },
            )
            put("structuredContent", structured)
            put("isError", isError)
        }
    }

    private fun initializeResult(params: JsonElement?): JsonObject {
        val requested = ((params as? JsonObject)?.get("protocolVersion") as? JsonPrimitive)?.contentOrNull
        return buildJsonObject {
            put("protocolVersion", requested?.takeIf { it in SUPPORTED_PROTOCOL_VERSIONS } ?: LATEST_PROTOCOL_VERSION)
            put(
                "capabilities",
                buildJsonObject {
                    put("tools", buildJsonObject {})
                },
            )
            put(
                "serverInfo",
                buildJsonObject {
                    put("name", "wynime-source-plugin-media-test")
                    put("version", "0.3.0")
                },
            )
        }
    }

    companion object {
        private const val LATEST_PROTOCOL_VERSION = "2025-06-18"
        private val SUPPORTED_PROTOCOL_VERSIONS = setOf("2024-11-05", "2025-03-26", LATEST_PROTOCOL_VERSION)
    }
}

@Serializable
data class RpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
data class RpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement,
    val result: JsonElement? = null,
    val error: RpcError? = null,
)

@Serializable
data class RpcError(
    val code: Int,
    val message: String,
)
