package com.wynime.app.domain.media.fetch

import com.wynime.app.data.models.preference.ProxyAuthorization
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyAuthorizationTest {
    @Test
    fun `toHeader encodes basic credentials`() {
        assertEquals(
            "Basic dXNlcjpwYXNz",
            ProxyAuthorization(username = "user", password = "pass").toHeader(),
        )
    }
}
