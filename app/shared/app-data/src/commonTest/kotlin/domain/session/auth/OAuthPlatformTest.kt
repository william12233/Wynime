package com.wynime.app.domain.session.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OAuthPlatformTest {
    @Test
    fun `ids match the provider ids of the server`() {
        assertEquals(OAuthPlatform.BANGUMI, OAuthPlatform.fromId("bangumi"))
        assertEquals(null, OAuthPlatform.fromId("github"))
    }

    @Test
    fun `unknown provider from a newer server is null instead of failing`() {
        assertNull(OAuthPlatform.fromId("google"))
        assertNull(OAuthPlatform.fromId(""))
        assertNull(OAuthPlatform.fromId("GitHub"))
    }
}
