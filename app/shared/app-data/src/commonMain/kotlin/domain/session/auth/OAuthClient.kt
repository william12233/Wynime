package com.wynime.app.domain.session.auth

import com.wynime.app.domain.session.AccessTokenPair

interface OAuthClient {

    suspend fun getOAuthRegisterLink(requestId: String): String

    suspend fun getOAuthBindLink(requestId: String): String

    suspend fun getResult(requestId: String): OAuthResult?
}

data class OAuthResult(
    val tokens: AccessTokenPair,
    val expiresInSeconds: Long,
    val refreshToken: String,
)

