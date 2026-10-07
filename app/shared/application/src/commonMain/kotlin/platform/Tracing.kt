package com.wynime.app.platform

import io.sentry.kotlin.multiplatform.Scope
import io.sentry.kotlin.multiplatform.SentryOptions
import com.wynime.app.trace.ErrorReport
import com.wynime.utils.platform.currentPlatform

internal object CommonTracingInitializer {
    fun configureSentryOptions(options: SentryOptions) {
        val buildConfig = currentWynimeBuildConfig
        options.dsn = buildConfig.sentryDsn
        options.debug = buildConfig.isDebug
        options.release = "com.wynime@${buildConfig.versionName}"
    }

    fun configureGlobalScope(scope: Scope) {
        val platform = currentPlatform()
        scope.setContext("os", platform.name)
        scope.setContext("arch", platform.arch.name)
        scope.setContext("version", currentWynimeBuildConfig.versionName)
    }
}

internal expect fun initializeSentry(userId: String)
