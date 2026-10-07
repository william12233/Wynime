package com.wynime.app.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavigationSerializationCompatibilityTest {
    @Test
    fun `previous settings navigation can be restored and saved`() {
        val payload = """{"type":"me.him188.ani.app.navigation.NavRoutes.Settings","tab":"PROFILE"}"""
        val restored = Json.decodeFromString<NavRoutes>(payload)
        assertEquals(NavRoutes.Settings(SettingsTab.PROFILE), restored)
        assertTrue(Json.encodeToString<NavRoutes>(restored).contains("me.him188.ani.app.navigation.NavRoutes.Settings"))
    }

    @Test
    fun `previous login navigation remains decodable`() {
        val payload = """{"type":"me.him188.ani.app.navigation.NavRoutes.QrLoginConfirm","requestId":"previous-request"}"""
        assertEquals(NavRoutes.QrLoginConfirm("previous-request"), Json.decodeFromString<NavRoutes>(payload))
    }
}
