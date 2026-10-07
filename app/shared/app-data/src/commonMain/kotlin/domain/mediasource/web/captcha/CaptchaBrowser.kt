package com.wynime.app.domain.mediasource.web.captcha

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.wynime.app.domain.mediasource.web.LoadedPage

data class BrowserCookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null,
    val expiresEpochMillis: Long? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
)

enum class InterceptDecision {

    Continue,

    Block,
}

interface CaptchaBrowser : AutoCloseable {

    val userAgent: String

    val pageLoads: SharedFlow<LoadedPage>

    val isLoading: StateFlow<Boolean>

    suspend fun navigate(url: String)

    suspend fun currentPage(): LoadedPage?

    suspend fun executeJavaScript(script: String)

    suspend fun collectCookies(urls: List<String>): List<BrowserCookie>

    fun setResourceInterceptor(handler: ((url: String) -> InterceptDecision)?)

    @Composable
    fun View(modifier: Modifier)
}

interface CaptchaBrowserFactory {

    val isSupported: Boolean

    val recommendedMaxSessions: Int get() = 3

    suspend fun create(): CaptchaBrowser
}

object UnsupportedCaptchaBrowserFactory : CaptchaBrowserFactory {
    override val isSupported: Boolean get() = false
    override suspend fun create(): CaptchaBrowser =
        throw UnsupportedOperationException("CaptchaBrowser is not supported on this platform")
}
