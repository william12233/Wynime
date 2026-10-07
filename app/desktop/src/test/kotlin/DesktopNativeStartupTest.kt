package com.wynime.app.desktop

import kotlinx.coroutines.runBlocking
import com.wynime.utils.platform.Arch
import com.wynime.utils.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopNativeStartupTest {
    @Test
    fun `player is prepared after JCEF when requested`() = runBlocking {
        val calls = mutableListOf<String>()

        initializeJcefAndPlayerBackend(
            preparePlayerBeforeJcef = false,
            preparePlayer = { calls += "player" },
            initializeJcef = { calls += "JCEF" },
        )

        assertEquals(listOf("JCEF", "player"), calls)
    }

    @Test
    fun `player is prepared before JCEF when requested`() = runBlocking {
        val calls = mutableListOf<String>()

        initializeJcefAndPlayerBackend(
            preparePlayerBeforeJcef = true,
            preparePlayer = { calls += "player" },
            initializeJcef = { calls += "JCEF" },
        )

        assertEquals(listOf("player", "JCEF"), calls)
    }

    @Test
    fun `Windows architectures prepare player before JCEF`() {
        assertTrue(shouldPreparePlayerBeforeJcef(Platform.Windows(Arch.X86_64)))
        assertTrue(shouldPreparePlayerBeforeJcef(Platform.Windows(Arch.AARCH64)))
    }
}
