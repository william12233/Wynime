package com.wynime.utils.ktor

import io.ktor.client.HttpClient

interface ApiInvoker<Api> {
    suspend operator fun <R> invoke(action: suspend Api.() -> R): R
}

fun <Api> ApiInvoker(
    client: ScopedHttpClient,
    getApi: (HttpClient) -> Api,
): ApiInvoker<Api> = object : ApiInvoker<Api> {
    override suspend fun <R> invoke(action: suspend Api.() -> R): R =
        client.use {
            action(
                getApi(this),
            )
        }
}
