@file:OptIn(UnsafeScopedHttpClientApi::class)

package com.wynime.app.domain.foundation

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.models.preference.ProxyConfig
import com.wynime.app.domain.foundation.DefaultHttpClientProvider.HoldingInstanceMatrix
import com.wynime.app.domain.settings.ProxyProvider
import com.wynime.test.DisabledOnAndroid
import com.wynime.test.TestContainer
import com.wynime.utils.ktor.UnsafeScopedHttpClientApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@DisabledOnAndroid
@Suppress("CanSealedSubClassBeObject")
sealed class DefaultHttpClientProviderTest {
    @TestContainer
    class SingleFeature : DefaultHttpClientProviderTest() {
        override fun TestScope.createProvider(proxyProvider: FakeProxyProvider): DefaultHttpClientProvider {
            return DefaultHttpClientProvider(
                proxyProvider = proxyProvider,
                backgroundScope = this,
                featureHandlers = listOf(UserAgentFeatureHandler),
            ).apply {
                backgroundScope.coroutineContext.job.invokeOnCompletion {
                    launch(NonCancellable) {
                        forceReleaseAll()
                    }
                }
            }
        }
    }

    @TestContainer
    class HasUnsetFeatures : DefaultHttpClientProviderTest() {
        override fun TestScope.createProvider(proxyProvider: FakeProxyProvider): DefaultHttpClientProvider {
            return DefaultHttpClientProvider(
                proxyProvider = proxyProvider,
                backgroundScope = this,
                featureHandlers = listOf(
                    UserAgentFeatureHandler,
                ),
            ).apply {
                backgroundScope.coroutineContext.job.invokeOnCompletion {
                    launch(NonCancellable) {
                        forceReleaseAll()
                    }
                }
            }
        }
    }

    protected class FakeProxyProvider : ProxyProvider {
        private val _proxy = MutableStateFlow<ProxyConfig?>(null)
        override val proxy: Flow<ProxyConfig?> = _proxy

        fun emit(newValue: ProxyConfig?) {
            _proxy.value = newValue
        }
    }

    protected abstract fun TestScope.createProvider(
        proxyProvider: FakeProxyProvider,
    ): DefaultHttpClientProvider

    private suspend fun DefaultHttpClientProvider.startProxyListening() {
        startProxyListening(
            sequence {
                for (userAgent in ScopedHttpClientUserAgent.entries) {
                    HoldingInstanceMatrix(
                        setOf(
                            UserAgentFeature.withValue(userAgent),
                        ),
                    )
                }
            },
        )
    }

    @Test
    fun `test get with same user agent reuses same client`() = runTest {
        val testProxyProvider = FakeProxyProvider()
        val provider = createProvider(testProxyProvider)

        val client1 = provider.get(ScopedHttpClientUserAgent.WYNIME).borrow()
        val client2 = provider.get(ScopedHttpClientUserAgent.WYNIME).borrow()

        assertEquals(client1, client2, "Expected equal HttpClient instance for the same user agent")

        provider.get(ScopedHttpClientUserAgent.WYNIME).returnClient(client1)
        provider.get(ScopedHttpClientUserAgent.WYNIME).returnClient(client2)
        provider.forceReleaseAll()
    }

    @Test
    fun `test get with different user agent returns different client`() = runTest {
        val testProxyProvider = FakeProxyProvider()
        val provider = createProvider(
            proxyProvider = testProxyProvider,
        )

        val wynimeClient = provider.get(ScopedHttpClientUserAgent.WYNIME).borrow()
        val browserClient = provider.get(ScopedHttpClientUserAgent.BROWSER).borrow()

        assertNotSame(
            wynimeClient,
            browserClient,
            "Expected different HttpClient instances for different user agents",
        )

        provider.get(ScopedHttpClientUserAgent.WYNIME).returnClient(wynimeClient)
        provider.get(ScopedHttpClientUserAgent.BROWSER).returnClient(browserClient)
        provider.forceReleaseAll()
    }

    @Test
    fun `test startProxyListening only once`() = runTest {
        val testProxyProvider = FakeProxyProvider()
        val provider = createProvider(
            proxyProvider = testProxyProvider,
        )

        assertFalse(provider.getProxyListeningStarted())

        provider.startProxyListening()
        assertTrue(provider.getProxyListeningStarted(), "Expected proxyListeningStarted to be true after first call")

        assertFailsWith<IllegalStateException> {
            provider.startProxyListening()
        }
        assertTrue(
            provider.getProxyListeningStarted(),
            "Expected proxyListeningStarted to remain true and do nothing on second call",
        )
        provider.forceReleaseAll()
    }

    @Test
    fun `test startProxyListening suspends and reads the first proxy`() = runTest {
        val testProxyProvider = FakeProxyProvider()
        val proxyConfig = ProxyConfig(url = "http://localhost:8080")
        testProxyProvider.emit(proxyConfig)
        val provider = createProvider(
            proxyProvider = testProxyProvider,
        )

        assertFalse(provider.getProxyListeningStarted())

        provider.startProxyListening()

        assertEquals(
            proxyConfig,
            provider.getCurrentProxyConfig(),
            "Expected to read the first proxy config when the function returns",
        )
        provider.forceReleaseAll()
    }

    @Test
    fun `test proxy listening updates clients when flow emits`() = runTest {
        val testProxyProvider = FakeProxyProvider()
        val provider = createProvider(
            proxyProvider = testProxyProvider,
        )

        provider.startProxyListening()
        assertEquals(null, provider.getCurrentProxyConfig())

        val wynimeWrapper = provider.get(ScopedHttpClientUserAgent.WYNIME)
        val wynimeClientBefore = wynimeWrapper.borrow()

        val newConfig = ProxyConfig(url = "http://localhost:9999")
        testProxyProvider.emit(newConfig)
        runCurrent()
        assertEquals(newConfig, provider.getCurrentProxyConfig())

        val wynimeClientAfter = wynimeWrapper.borrow()

        assertNotSame(
            wynimeClientBefore,
            wynimeClientAfter,
            "Expected a new HttpClient instance once the proxy config changes",
        )

        wynimeWrapper.returnClient(wynimeClientBefore)
        wynimeWrapper.returnClient(wynimeClientAfter)
        provider.forceReleaseAll()
    }

    @Test
    fun `test repeated proxy changes`() = runTest {
        val testProxyProvider = FakeProxyProvider()
        val provider = createProvider(
            proxyProvider = testProxyProvider,
        )

        provider.startProxyListening()

        val wrapper = provider.get(ScopedHttpClientUserAgent.WYNIME)
        val firstClient = wrapper.borrow()

        testProxyProvider.emit(ProxyConfig(url = "http://first-change:8080"))
        runCurrent()
        val secondClient = wrapper.borrow()

        assertNotSame(
            firstClient,
            secondClient,
            "Expected new client after the first proxy update",
        )

        testProxyProvider.emit(ProxyConfig(url = "http://second-change:9090"))
        runCurrent()
        val thirdClient = wrapper.borrow()

        assertNotSame(
            secondClient,
            thirdClient,
            "Expected new client after the second proxy update",
        )

        wrapper.returnClient(firstClient)
        wrapper.returnClient(secondClient)
        wrapper.returnClient(thirdClient)
        provider.forceReleaseAll()
    }
}
