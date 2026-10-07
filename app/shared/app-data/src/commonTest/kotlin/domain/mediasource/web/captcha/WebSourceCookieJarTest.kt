package com.wynime.app.domain.mediasource.web.captcha

import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebSourceCookieJarTest {

    @Test
    fun `domain cookie covers subdomains`() = runTest {
        val jar = WebSourceCookieJar()
        jar.addBrowserCookies(
            "https://www.example.com/search",
            listOf(
                BrowserCookie(name = "cf_clearance", value = "token", domain = ".example.com", path = "/"),
            ),
        )

        assertEquals(
            listOf("cf_clearance=token"),
            jar.getCookieHeaderValues("https://play.example.com/embed/1"),
        )
        assertEquals(
            listOf("cf_clearance=token"),
            jar.getCookieHeaderValues("https://example.com/search"),
        )

        assertEquals(emptyList(), jar.getCookieHeaderValues("https://other.com/"))
    }

    @Test
    fun `host only cookie matches www variant but not sibling subdomain`() = runTest {
        val jar = WebSourceCookieJar()

        jar.addBrowserCookies(
            "https://www.example.com/search",
            listOf(BrowserCookie(name = "PHPSESSID", value = "abc")),
        )

        assertEquals(listOf("PHPSESSID=abc"), jar.getCookieHeaderValues("https://example.com/a"))
        assertEquals(listOf("PHPSESSID=abc"), jar.getCookieHeaderValues("https://www.example.com/a"))
        assertEquals(emptyList(), jar.getCookieHeaderValues("https://play.example.com/a"))
    }

    @Test
    fun `ktor storage roundtrip with expiry`() = runTest {
        val jar = WebSourceCookieJar()
        jar.addCookie(
            Url("https://example.com/"),
            Cookie(name = "expired", value = "x", maxAge = -1),
        )
        jar.addCookie(
            Url("https://example.com/"),
            Cookie(name = "alive", value = "y"),
        )

        val cookies = jar.get(Url("https://example.com/"))
        assertEquals(listOf("alive"), cookies.map { it.name })
    }

    @Test
    fun `secure cookie is not sent over http`() = runTest {
        val jar = WebSourceCookieJar()
        jar.addBrowserCookies(
            "https://example.com/",
            listOf(BrowserCookie(name = "cf_clearance", value = "t", domain = ".example.com", secure = true)),
        )

        assertEquals(emptyList(), jar.getCookieHeaderValues("http://example.com/"))
        assertEquals(listOf("cf_clearance=t"), jar.getCookieHeaderValues("https://example.com/"))
    }

    @Test
    fun `clearForHost drops host and subdomain cookies`() = runTest {
        val jar = WebSourceCookieJar()
        jar.addBrowserCookies(
            "https://example.com/",
            listOf(
                BrowserCookie(name = "a", value = "1", domain = ".example.com"),
                BrowserCookie(name = "b", value = "2"),
            ),
        )
        jar.addBrowserCookies(
            "https://other.com/",
            listOf(BrowserCookie(name = "c", value = "3")),
        )

        jar.clearForHost("example.com")

        assertEquals(emptyList(), jar.getCookieHeaderValues("https://example.com/"))
        assertEquals(listOf("c=3"), jar.getCookieHeaderValues("https://other.com/"))
    }

    @Test
    fun `same name cookie is replaced`() = runTest {
        val jar = WebSourceCookieJar()
        jar.addBrowserCookies("https://example.com/", listOf(BrowserCookie(name = "k", value = "old")))
        jar.addBrowserCookies("https://example.com/", listOf(BrowserCookie(name = "k", value = "new")))

        assertEquals(listOf("k=new"), jar.getCookieHeaderValues("https://example.com/"))
    }

    @Test
    fun `identity registry matches host and parent domain only`() {
        val registry = WebSourceIdentityRegistry()
        registry.setUserAgent("www.example.com", "RealBrowserUA/1.0")

        assertEquals("RealBrowserUA/1.0", registry.userAgentFor("example.com"))
        assertEquals("RealBrowserUA/1.0", registry.userAgentFor("www.example.com"))

        assertEquals("RealBrowserUA/1.0", registry.userAgentFor("play.example.com"))

        assertNull(registry.userAgentFor("other.com"))
        assertTrue(registry.userAgentFor("com") == null)
    }
}
