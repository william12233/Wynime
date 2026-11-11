package tw.wynime.sources.baimao

internal object BaimaoChallenge {
    private const val BROWSER_FACTOR = 1

    fun siteSeconds(serverTime: Long, timeRequestMillis: Long, nowMillis: Long): Long {
        val requestSeconds = timeRequestMillis / 1000L
        return nowMillis / 1000L + (serverTime - requestSeconds)
    }

    fun m2t(seconds: Long): String {
        val value = seconds shr (0x11 + BROWSER_FACTOR)
        return (
            (((((value * 0x11 + 0xbd) * (value % 0x3f + 0xf)) *
                (value % 0x1f + 0x21)) * (value % 0x11 + 0x70)) *
                (value % 0x7 + 0x43)) + 0x17b
            ).toString()
    }

    fun im(seconds: Long): String = challengeValue(seconds shr (0x11 + BROWSER_FACTOR))

    fun kr(seconds: Long): String = challengeValue(seconds shr 0x7)

    private fun challengeValue(value: Long): String = (
        (((value * 0xd + 0x113) * (value % 0x21 + 0xb)) *
            (value % 0x11 + 0x3)) + 0x387
        ).toString()
}
