package com.wynime.utils.analytics

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics

class AnalyticsImpl(
    config: AnalyticsConfig,
) : CommonAnalyticsImpl(config), IAnalytics {

    fun init() {
        val analytics = Firebase.analytics
        analytics.setAnalyticsCollectionEnabled(true)
        analytics.setDefaultEventParameters(
            intrinsicProperties.map { sanitizeParamKey(it.key) to it.value.toString() }.toMap(),
        )
    }

    override fun recordEventImpl(event: AnalyticsEvent, properties: Map<String, Any>) {
        val name = sanitizeEventName(event.event)
        val payload: Map<String, Any> = properties.map { (k, v) ->
            sanitizeParamKey(k) to v
        }.toMap()
        Firebase.analytics.logEvent(name, payload)
    }

    override fun onAppStart() {
    }
}

