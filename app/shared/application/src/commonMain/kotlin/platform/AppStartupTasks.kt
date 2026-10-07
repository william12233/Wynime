package com.wynime.app.platform

import com.wynime.app.platform.trace.SentryErrorReport
import com.wynime.app.trace.ErrorReportHolder
import com.wynime.utils.analytics.AnalyticsConfig
import com.wynime.utils.analytics.AnalyticsHolder
import com.wynime.utils.analytics.IAnalytics
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger
import com.wynime.utils.platform.currentPlatform

object AppStartupTasks {
    fun initializeSentry(userId: String) {
        if (!currentWynimeBuildConfig.isDebug && currentWynimeBuildConfig.sentryEnabled) {
            ErrorReportHolder.init(SentryErrorReport)
            com.wynime.app.platform.initializeSentry(userId = userId)
        } else {

        }
    }

    inline fun initializeAnalytics(instance: () -> IAnalytics) {
        if (currentWynimeBuildConfig.analyticsEnabled) {
            AnalyticsHolder.init(instance())
        }
    }

    fun printVersions() {
        logger.info { "Wynime started. platform: ${currentPlatform()}, version: ${currentWynimeBuildConfig.versionName}, isDebug: ${currentWynimeBuildConfig.isDebug}" }
    }

    private val logger = logger<AppStartupTasks>()
}

fun AnalyticsConfig.Companion.create(): AnalyticsConfig {
    return AnalyticsConfig(
        currentWynimeBuildConfig.versionName,
        currentWynimeBuildConfig.isDebug,
    )
}
