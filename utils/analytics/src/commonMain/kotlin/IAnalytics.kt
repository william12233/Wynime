package com.wynime.utils.analytics

import com.wynime.utils.platform.currentPlatform
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmInline

val Analytics: IAnalytics get() = AnalyticsHolder.getInstance()

interface IAnalytics {
    fun recordEvent(
        event: AnalyticsEvent,
        properties: Map<String, Any?> = emptyMap(),
    )

    fun onAppStart() {}
}

inline fun IAnalytics.recordEvent(
    event: AnalyticsEvent,
    buildProperties: MutableMap<String, Any?>.() -> Unit = {},
) {
    recordEvent(event, buildMap(buildProperties))
}

@JvmInline
value class AnalyticsEvent(val event: String) {
    companion object {
        val Screen = AnalyticsEvent("screen")
        val AppStart = Screen
        val SessionStart = AnalyticsEvent("session_start")

        val AppServerTestSuccess = AnalyticsEvent("app_server_test_success")
        val AppServerTestError = AnalyticsEvent("app_server_test_error")
        val AppServerSelectError = AnalyticsEvent("app_server_select_error")

        val NetworkCheckFailed = AnalyticsEvent("network_check_failed")
        val LoginClick = AnalyticsEvent("login_click")
        val LoginBangumiSuccess = AnalyticsEvent("login_bangumi_success")
        val LoginSkipClick = AnalyticsEvent("login_skip_click")

        val EpisodeEnter = AnalyticsEvent("episode_enter")
        val EpisodeSwitch = AnalyticsEvent("episode_switch")
        val EpisodePlaying = AnalyticsEvent("episode_playing")

        val CacheCreate = AnalyticsEvent("cache_create")

        val RatingEnter = AnalyticsEvent("rating_enter")
        val RatingSubmit = AnalyticsEvent("rating_submit")

        val SearchStart = AnalyticsEvent("search_start")
        val SubjectEnter = AnalyticsEvent("subject_enter")
        val SubjectRecommendationClick = AnalyticsEvent("subject_recommendation_click")
    }
}

data class AnalyticsConfig(
    val appVersion: String,
    val debugLogging: Boolean,
) {
    companion object
}

abstract class CommonAnalyticsImpl(
    protected val config: AnalyticsConfig,
) : IAnalytics {
    protected val intrinsicProperties: Map<String, Any> = buildMap {
        val platform = currentPlatform()
        put("os", platform.name)
        put("arch", platform.arch.name)
        put("\$app_version", config.appVersion)
    }

    final override fun recordEvent(event: AnalyticsEvent, properties: Map<String, Any?>) {
        @Suppress("UNCHECKED_CAST")
        recordEventImpl(
            event,
            properties.filterValues { it != null } as Map<String, Any>,
        )
    }

    protected abstract fun recordEventImpl(event: AnalyticsEvent, properties: Map<String, Any> = emptyMap())

    protected fun sanitizeEventName(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
        return cleaned.ifEmpty { "event" }.take(40)
    }

    protected fun sanitizeParamKey(name: String): String {
        val noDollar = name.removePrefix("$")
        val cleaned = noDollar.replace(Regex("[^A-Za-z0-9_]+"), "_")
        val startAlpha = if (cleaned.firstOrNull()?.isLetter() == true) cleaned else "p_$cleaned"
        return startAlpha.take(40)
    }
}

object AnalyticsHolder {
    @Volatile
    private var instance: IAnalytics = NoopAnalytics

    internal fun getInstance(): IAnalytics = instance

    fun init(instance: IAnalytics) {
        check(this.instance === NoopAnalytics) { "Analytics instance is already initialized" }
        this.instance = instance
    }
}

private object NoopAnalytics : IAnalytics {
    override fun recordEvent(event: AnalyticsEvent, properties: Map<String, Any?>) {
    }

    override fun onAppStart() {
    }
}
