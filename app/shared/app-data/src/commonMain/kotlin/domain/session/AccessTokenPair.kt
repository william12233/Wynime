package com.wynime.app.domain.session

import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

data class AccessTokenPair(
    val legacyServiceAccessToken: String,
    val expiresAtMillis: Long,
    val bangumiAccessToken: String?,
) {
    override fun toString(): String {

        return "AccessTokenPair(bangumiAccessToken.hashCode=${bangumiAccessToken.hashCode()}, aniAccessToken.hashCode=${legacyServiceAccessToken.hashCode()}, expiresAtMillis=$expiresAtMillis)"
    }
}

fun AccessTokenPair.isExpired(clock: Clock = Clock.System): Boolean {
    return expiresAtMillis <=
            (clock.now().toEpochMilliseconds() + 1.hours.inWholeMilliseconds)
}
