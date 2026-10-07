package com.wynime.app.domain.mediasource.web.captcha

import kotlinx.coroutines.CoroutineScope
import com.wynime.app.domain.mediasource.web.PageEvaluator
import com.wynime.utils.ktor.asScopedHttpClient
import com.wynime.utils.ktor.createDefaultHttpClient
import com.wynime.utils.platform.annotations.TestOnly

@TestOnly
fun createTestWebSessionManager(
    backgroundScope: CoroutineScope,
): WebSessionManager = WebSessionManager(
    browserFactory = UnsupportedCaptchaBrowserFactory,
    evaluator = PageEvaluator(),
    cookieJar = WebSourceCookieJar(),
    identityRegistry = WebSourceIdentityRegistry(),
    client = createDefaultHttpClient().asScopedHttpClient(),
    backgroundScope = backgroundScope,
)
