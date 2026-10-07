package com.wynime.datasources.api.source

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import com.wynime.datasources.api.source.parameter.MediaSourceParameter
import com.wynime.datasources.api.source.parameter.MediaSourceParameters
import com.wynime.utils.ktor.ClientProxyConfig
import com.wynime.utils.ktor.ScopedHttpClient
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class FactoryId(

    val value: String,
) {
    override fun toString(): String = value
}

interface MediaSourceFactory {

    val factoryId: FactoryId

    val allowMultipleInstances: Boolean get() = false

    val parameters: MediaSourceParameters get() = MediaSourceParameters.Empty

    val info: MediaSourceInfo

    fun create(
        mediaSourceId: String,
        config: MediaSourceConfig,
        client: ScopedHttpClient,
    ): MediaSource
}

@Serializable
data class MediaSourceConfig(
    @Deprecated("unused anymore. MediaSources now accept ScopedHttpClient instances instead of maintaining clients on their own.")
    val proxy: ClientProxyConfig? = null,
    @Deprecated("unused anymore. MediaSources now accept ScopedHttpClient instances instead of maintaining clients on their own.")
    val userAgent: String? = null,

    val arguments: Map<String, String?> = mapOf(),

    val serializedArguments: JsonElement? = null,

    val subscriptionId: String? = null,
) {
    companion object {
        val Default = MediaSourceConfig()
    }
}

operator fun <T> MediaSourceConfig.get(parameter: MediaSourceParameter<T>): T =
    arguments[parameter.name]?.let { parameter.parseFromString(it) } ?: parameter.default()

private val parametersJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun <T> MediaSourceConfig.deserializeArgumentsOrNull(
    deserializationStrategy: DeserializationStrategy<T>,
    json: Json = parametersJson,
): T? = serializedArguments?.let { json.decodeFromJsonElement(deserializationStrategy, it) }

fun <T> MediaSourceConfig.Companion.serializeArguments(
    serializationStrategy: SerializationStrategy<T>,
    value: T,
    json: Json = parametersJson,
): JsonElement = json.encodeToJsonElement(serializationStrategy, value)

fun <T> MediaSourceConfig.Companion.deserializeArgumentsFromString(
    deserializationStrategy: DeserializationStrategy<T>,
    value: String,
    json: Json = parametersJson,
): T = json.decodeFromString(deserializationStrategy, value)

fun <T> MediaSourceConfig.Companion.serializeArgumentsToString(
    serializationStrategy: SerializationStrategy<T>,
    value: T,
    json: Json = parametersJson,
): String = json.encodeToString(serializationStrategy, value)

