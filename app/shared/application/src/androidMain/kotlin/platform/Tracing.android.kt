package com.wynime.app.platform

import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.protocol.User

internal actual fun initializeSentry(userId: String) {
    Sentry.init { options ->
        CommonTracingInitializer.configureSentryOptions(options)
    }
    Sentry.configureScope {
        it.user = User(id = userId)
        CommonTracingInitializer.configureGlobalScope(it)
    }
}
