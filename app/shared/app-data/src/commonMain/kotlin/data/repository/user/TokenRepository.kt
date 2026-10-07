package com.wynime.app.data.repository.user

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.wynime.app.domain.session.AccessTokenPair
import com.wynime.app.domain.session.isExpired

class TokenRepository(
    private val dataStore: DataStore<TokenSave>
) {
    val refreshToken: Flow<String?> = dataStore.data.map { it.refreshToken }
    suspend fun setRefreshToken(value: String) {
        dataStore.updateData {
            it.copy(refreshToken = value)
        }
    }

    val session: Flow<Session> = dataStore.data.map { save ->
        when {
            save.accessTokens != null -> {
                AccessTokenSession(
                    AccessTokenPair(
                        legacyServiceAccessToken = save.accessTokens.legacyServiceAccessToken,
                        expiresAtMillis = save.accessTokens.expiresAtMillis,
                        bangumiAccessToken = save.accessTokens.bangumiAccessToken,
                    ),
                )
            }

            else -> GuestSession
        }
    }

    suspend fun setSession(session: Session) {
        when (session) {
            is AccessTokenSession -> {
                dataStore.updateData {
                    it.copy(
                        accessTokens = TokenSave.AccessTokens(
                            bangumiAccessToken = session.tokens.bangumiAccessToken,
                            legacyServiceAccessToken = session.tokens.legacyServiceAccessToken,
                            expiresAtMillis = session.tokens.expiresAtMillis,
                        ),
                    )
                }
            }

            GuestSession -> {
                dataStore.updateData {
                    it.copy(
                        refreshToken = null,
                        accessTokens = null,
                    )
                }
            }
        }
    }

    suspend fun clear() {
        dataStore.updateData {
            it.copy(
                refreshToken = null,
                accessTokens = null,
            )
        }
    }

    suspend fun getTokenSaveSnapshot(): TokenSave {
        return dataStore.data.map { it }.first()
    }

    suspend fun restoreFromTokenSave(save: TokenSave) {
        dataStore.updateData { save }
    }
}

@ConsistentCopyVisibility
@Serializable
data class TokenSave internal constructor(
    val refreshToken: String? = null,
    val accessTokens: AccessTokens? = null,
) {
    @Serializable
    data class AccessTokens(
        val bangumiAccessToken: String?,
        @SerialName("aniAccessToken") val legacyServiceAccessToken: String,
        val expiresAtMillis: Long,
    )

    companion object {
        val Initial = TokenSave()
    }
}

sealed interface Session

data object GuestSession : Session

data class AccessTokenSession(
    val tokens: AccessTokenPair,
) : Session {
    @Deprecated("Use this.tokens.expiresAtMillis instead.", replaceWith = ReplaceWith("this.tokens.expiresAtMillis"))
    val expiresAtMillis: Long get() = tokens.expiresAtMillis
}

@Deprecated(
    "",
    replaceWith = ReplaceWith(
        "!tokens.isExpired()",
        "com.wynime.app.domain.session.isExpired",
    ),
)
fun AccessTokenSession.isValid() = !tokens.isExpired()

@Deprecated(
    "",
    replaceWith = ReplaceWith(
        "tokens.isExpired()",
        "com.wynime.app.domain.session.isExpired",
    ),
)
fun AccessTokenSession.isExpired() = tokens.isExpired()

class LegacyTokenRepository(
    store: DataStore<Preferences>,
) {
    private companion object Keys {
        val USER_ID = longPreferencesKey("user_id")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")

        val IS_GUEST = stringPreferencesKey("is_guest")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val ACCESS_TOKEN_EXPIRE_AT = longPreferencesKey("access_token_expire_at")
    }

    private val tokenStore = store

    val refreshToken: Flow<String?> = tokenStore.data.map { it[REFRESH_TOKEN] }

    suspend fun setRefreshToken(value: String) {
        tokenStore.edit { it[REFRESH_TOKEN] = value }
    }

    val session: Flow<Session?> = tokenStore.data.map { preferences ->
        val accessToken = preferences[ACCESS_TOKEN]
        val expireAt = preferences[ACCESS_TOKEN_EXPIRE_AT]
        val isGuest = preferences[IS_GUEST]?.toBooleanStrict()
        if (isGuest == true) {
            GuestSession
        } else {
            if (accessToken == null || expireAt == null) {
                return@map null
            }
            AccessTokenSession(
                AccessTokenPair(
                    "",
                    expiresAtMillis = expireAt,
                    accessToken,
                ),
            )
        }
    }

    suspend fun clear() {
        tokenStore.edit {
            it.remove(USER_ID)
            it.remove(ACCESS_TOKEN)
            it.remove(ACCESS_TOKEN_EXPIRE_AT)
            it.remove(REFRESH_TOKEN)
            it.remove(IS_GUEST)
        }
    }
}
