package com.wynime.app.data.models

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.io.IOException
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmInline

sealed interface ApiFailure {
    data object Unauthorized : ApiFailure
    data object NetworkError : ApiFailure
    data object ServiceUnavailable : ApiFailure
}

@JvmInline
value class ApiResponse<out T> private constructor(
    private val value: Any?,
) {
    val isSuccess: Boolean get() = !isFailure

    val isFailure: Boolean get() = value is ApiFailure

    fun getOrNull(): T? =
        @Suppress("UNCHECKED_CAST")
        if (isSuccess) value as T else null

    fun failureOrNull(): ApiFailure? = value as? ApiFailure

    fun getOrThrow(): T {
        val value = value
        @Suppress("UNCHECKED_CAST")
        return if (isSuccess) value as T else {
            check(value is ApiFailure)
            throw IllegalStateException("Request failed: $value")
        }
    }

    companion object {
        fun <T> success(value: T): ApiResponse<T> {
            require(value !is ApiFailure) { "value must not be a RequestFailure" }
            return ApiResponse(value)
        }

        fun <T> failure(failure: ApiFailure): ApiResponse<T> {
            return ApiResponse(failure)
        }
    }
}

fun <T> ApiResponse.Companion.unauthorized(): ApiResponse<T> = failure(ApiFailure.Unauthorized)
fun <T> ApiResponse.Companion.networkError(): ApiResponse<T> = failure(ApiFailure.NetworkError)
fun <T> ApiResponse.Companion.serviceUnavailable(): ApiResponse<T> = failure(ApiFailure.ServiceUnavailable)

inline fun <T> runApiRequest(block: () -> T): ApiResponse<T> {
    try {
        return ApiResponse.success(block())
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.Unauthorized || e.response.status == HttpStatusCode.Forbidden) {
            return ApiResponse.failure(ApiFailure.Unauthorized)
        }
        throw IllegalStateException("runApiRequest failed, see cause", e)
    } catch (e: ServerResponseException) {
        return ApiResponse.failure(ApiFailure.ServiceUnavailable)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        return ApiResponse.failure(ApiFailure.NetworkError)
    } catch (e: Exception) {
        throw IllegalStateException("runApiRequest failed, see cause", e)
    }
}

inline fun <R, T> R.runApiRequest(block: R.() -> T): ApiResponse<T> =
    com.wynime.app.data.models.runApiRequest { block() }

inline fun <T, R> ApiResponse<T>.map(transform: (T) -> R): ApiResponse<R> {
    contract { callsInPlace(transform, InvocationKind.AT_MOST_ONCE) }
    return if (isSuccess) {
        @Suppress("UNCHECKED_CAST")
        ApiResponse.success(transform(getOrNull() as T))
    } else {
        @Suppress("UNCHECKED_CAST")
        this as ApiResponse<R>
    }
}

inline fun <T : R, R> ApiResponse<T>.valueOrElse(
    block: (ApiFailure) -> R,
): R {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return if (isSuccess) {
        @Suppress("UNCHECKED_CAST")
        getOrNull() as T
    } else {
        block(failureOrNull()!!)
    }
}

inline fun <T, R> ApiResponse<T>.fold(
    onSuccess: (value: T) -> R,
    onKnownFailure: (ApiFailure) -> R,
): R {
    contract {
        callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
        callsInPlace(onKnownFailure, InvocationKind.AT_MOST_ONCE)
    }

    @Suppress("UNCHECKED_CAST")
    return when {
        isSuccess -> onSuccess(getOrNull() as T)
        else -> onKnownFailure(failureOrNull()!!)
    }
}

inline fun <T, R> ApiResponse<T>.flatMap(
    onSuccess: (value: T) -> ApiResponse<R>,
): ApiResponse<R> {
    contract {
        callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
    }

    @Suppress("UNCHECKED_CAST")
    return when {
        isSuccess -> onSuccess(getOrNull() as T)
        else -> this as ApiResponse<R>
    }
}
