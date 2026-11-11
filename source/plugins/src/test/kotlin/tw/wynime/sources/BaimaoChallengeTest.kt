package tw.wynime.sources

import kotlin.test.Test
import kotlin.test.assertEquals
import tw.wynime.sources.baimao.BaimaoChallenge

class BaimaoChallengeTest {
    @Test
    fun `challenge values follow the current public player formula`() {
        val serverTime = 1_791_606_027L

        assertEquals(
            serverTime,
            BaimaoChallenge.siteSeconds(
                serverTime = serverTime,
                timeRequestMillis = serverTime * 1000,
                nowMillis = serverTime * 1000 + 456,
            ),
        )
        assertEquals("1901986032619", BaimaoChallenge.m2t(serverTime))
        assertEquals("3743817", BaimaoChallenge.im(serverTime))
        assertEquals("26202278487", BaimaoChallenge.kr(serverTime))
    }
}
