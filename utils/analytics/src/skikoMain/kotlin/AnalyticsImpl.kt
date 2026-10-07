package com.wynime.utils.analytics

import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import com.wynime.utils.coroutines.childScope
import com.wynime.utils.ktor.createDefaultHttpClient
import com.wynime.utils.logging.debug
import com.wynime.utils.logging.logger
import com.wynime.utils.logging.warn
import com.wynime.utils.platform.currentTimeMillis
import com.wynime.utils.serialization.toJsonElement
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

class AnalyticsSecrets(
    val apiSecret: String,
    val firebaseAppId: String,
)

class AnalyticsImpl(
    config: AnalyticsConfig,
    private val appInstanceId: String,
    private val userId: suspend () -> Uuid?,
    private val secrets: AnalyticsSecrets,
    parentCoroutineContext: CoroutineContext,
) : CommonAnalyticsImpl(config), IAnalytics {
    @kotlin.concurrent.Volatile
    private var _initialized = false
    private val client = createDefaultHttpClient()
    private val scope = CoroutineScope(parentCoroutineContext).childScope().apply {
        this.coroutineContext.job.invokeOnCompletion {
            client.close()
        }
    }

    private data class RecordEvent(
        val event: AnalyticsEvent,
        val properties: Map<String, Any>,
        val timestampMicros: Long = currentTimeMillis() * 1000,
    )

    private val eventQueue = Channel<RecordEvent>(1000, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var lastEventTime = TimeSource.Monotonic.markNow()
    private val sessionId = Random.nextInt(0, Int.MAX_VALUE)

    private val logger = logger<AnalyticsImpl>()

    fun init() {
        if (_initialized) return
        _initialized = true
        scope.launch(CoroutineName("AnalyticsWorker")) {
            while (isActive) {
                val event = eventQueue.receive()
                try {
                    postEvent(event)
                    lastEventTime = TimeSource.Monotonic.markNow()
                } catch (e: Throwable) {
                    logger.warn(e) { "Failed to post event: ${event.event}" }
                }
            }
        }

        onAppStart()
    }

    override fun onAppStart() {

    }

    override fun recordEventImpl(event: AnalyticsEvent, properties: Map<String, Any>) {
        if (_initialized && !eventQueue.trySend(RecordEvent(event, properties)).isSuccess) {
            logger.warn { "Failed to enqueue event: $event" }
        }
    }

    private suspend fun postEvent(
        record: RecordEvent,
    ) {
        val name = sanitizeEventName(record.event.event)
        val payload: Map<String, Any> = buildMap {
            fun putSafe(k: String, v: Any) {
                put(sanitizeParamKey(k), v)
            }

            record.properties.forEach { (k, v) ->
                putSafe(k, v)
            }
            intrinsicProperties.forEach { (k, v) ->
                putSafe(k, v)
            }
            putSafe("session_id", sessionId)

            putSafe("engagement_time_msec", lastEventTime.elapsedNow().inWholeMilliseconds.coerceAtLeast(10_000))
        }

        val resp = client.post("https://www.google-analytics.com/mp/collect") {

            parameter("firebase_app_id", secrets.firebaseAppId)
            parameter("api_secret", secrets.apiSecret)
            contentType(ContentType.Application.Json)

            setBody(
                Req(
                    app_instance_id = appInstanceId.replace("-", ""),
                    user_id = userId()?.toHexDashString(),
                    events = listOf(
                        Event(
                            name = name,
                            params = payload.toJsonElement(),
                            timestamp_micros = record.timestampMicros,
                        ),
                    ),
                ),
            )
        }
        logger.debug { "Posted event: ${record.event}, response status: ${resp.status}, response body: ${resp.bodyAsText()}" }
    }

}

@Serializable
private data class Event(
    val name: String,
    val params: JsonElement,
    val timestamp_micros: Long,
)

@Serializable
private data class Req(
    val app_instance_id: String,
    val user_id: String?,

    val events: List<Event>,
)

