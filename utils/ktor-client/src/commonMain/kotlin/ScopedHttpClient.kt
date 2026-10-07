package com.wynime.utils.ktor

import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import kotlin.contracts.contract

abstract class ScopedHttpClient {

    @OptIn(UnsafeScopedHttpClientApi::class)
    inline fun <R> use(
        action: HttpClient.() -> R,
    ): R {
        contract {
            callsInPlace(action, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
        }
        val client = borrow()
        try {
            return action(client.client)
        } finally {
            returnClient(client)
        }
    }

    @UnsafeScopedHttpClientApi
    abstract fun borrow(): Ticket

    @UnsafeScopedHttpClientApi
    fun borrowForever(): Ticket = borrow()

    @UnsafeScopedHttpClientApi
    abstract fun returnClient(ticket: Ticket)

    @SubclassOptInRequired(UnsafeScopedHttpClientApi::class)
    @UnsafeScopedHttpClientApi
    interface Ticket {
        val client: HttpClient
    }
}

@RequiresOptIn(
    message = "This operates on unsafe reference counter. Incorrect usage may cause memory leak.",
    level = RequiresOptIn.Level.ERROR,
)
annotation class UnsafeScopedHttpClientApi

fun HttpClient.asScopedHttpClient(): ScopedHttpClient = object : ScopedHttpClient() {
    @UnsafeScopedHttpClientApi
    private val ticket = object : Ticket {
        override val client = this@asScopedHttpClient
    }

    @UnsafeScopedHttpClientApi
    override fun borrow(): Ticket {
        return ticket
    }

    @UnsafeScopedHttpClientApi
    override fun returnClient(ticket: Ticket) {
    }
}

