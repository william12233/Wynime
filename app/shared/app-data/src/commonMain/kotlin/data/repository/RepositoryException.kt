package com.wynime.app.data.repository

import androidx.paging.PagingSource
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.io.IOException
import com.wynime.app.data.repository.RepositoryException.Companion.wrapOrThrowCancellation
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName

sealed class RepositoryException : Exception {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)

    companion object {
        fun wrapOrThrowCancellation(cause: Throwable): RepositoryException = when (cause) {
            is CancellationException -> throw cause
            is RepositoryException -> cause
            is ClientRequestException -> {
                when (cause.response.status) {
                    HttpStatusCode.Unauthorized -> RepositoryAuthorizationException(cause.response.status.description)
                    HttpStatusCode.Forbidden -> RepositoryAuthorizationException(cause.response.status.description)
                    HttpStatusCode.TooManyRequests -> RepositoryRateLimitedException(cause.response.status.description)
                    else -> {
                        RepositoryUnknownException(cause)
                    }
                }
            }

            is IOException -> RepositoryNetworkException(null, cause)
            is ServerResponseException -> RepositoryServiceUnavailableException(cause.response.status.description)

            else -> {
                RepositoryUnknownException(cause)
            }
        }

    }
}

inline fun <K : Any, V : Any> runWrappingExceptionAsLoadResult(block: () -> PagingSource.LoadResult<K, V>): PagingSource.LoadResult<K, V> {
    return try {
        block()
    } catch (e: Throwable) {
        PagingSource.LoadResult.Error(wrapOrThrowCancellation(e))
    }
}

class RepositoryAuthorizationException(message: String? = null, cause: Throwable? = null) :
    RepositoryException(message, cause)

class RepositoryNetworkException(message: String? = null, cause: Throwable? = null) :
    RepositoryException(message, cause)

class RepositoryServiceUnavailableException(message: String? = null, cause: Throwable? = null) :
    RepositoryException(message, cause)

class RepositoryRateLimitedException(message: String? = null, cause: Throwable? = null) :
    RepositoryException(message, cause)

class RepositoryRequestError(
    @get:JvmName("getLocalizedMessage0") val localizedMessage: String,
    message: String? = null,
    cause: Throwable? = null,
) : RepositoryException(message, cause)

class RepositoryUnknownException(throwable: Throwable) : RepositoryException(null, cause = throwable)

val PagingSource.LoadResult.Error<*, *>.repositoryException: RepositoryException?
    get() = throwable as? RepositoryException

fun RepositoryException.shouldRetry() = when (this) {
    is RepositoryAuthorizationException -> false
    is RepositoryNetworkException -> true
    is RepositoryRateLimitedException -> false
    is RepositoryServiceUnavailableException -> false
    is RepositoryUnknownException -> false
    is RepositoryRequestError -> false
}

fun RepositoryException.Companion.shouldRetry(throwable: Throwable): Boolean {
    return when (throwable) {
        is RepositoryException -> throwable.shouldRetry()
        is CancellationException -> false
        else -> {
            RepositoryException.wrapOrThrowCancellation(throwable).shouldRetry()
        }
    }
}
