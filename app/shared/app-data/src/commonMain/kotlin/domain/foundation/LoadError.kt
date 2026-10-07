package com.wynime.app.domain.foundation

import androidx.paging.CombinedLoadStates
import com.wynime.app.data.repository.RepositoryAuthorizationException
import com.wynime.app.data.repository.RepositoryNetworkException
import com.wynime.app.data.repository.RepositoryRateLimitedException
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.app.data.repository.RepositoryServiceUnavailableException
import com.wynime.app.tools.paging.exceptions
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException

sealed class LoadError {
    data object NoResults : LoadError()
    data object RequiresLogin : LoadError()
    data object NetworkError : LoadError()
    data object ServiceUnavailable : LoadError()
    data object RateLimited : LoadError()
    data class RequestError(val localized: String, val throwable: Throwable?) : LoadError()
    data class UnknownError(val throwable: Throwable?) : LoadError()

    companion object {
        fun fromCombinedLoadStates(states: CombinedLoadStates): LoadError? {
            if (!states.hasError) {
                return null
            }
            val exceptions = states.exceptions()
            for (e in exceptions) {
                when (e) {
                    is RepositoryAuthorizationException -> return RequiresLogin
                    is RepositoryNetworkException -> return NetworkError
                    is RepositoryServiceUnavailableException -> return ServiceUnavailable
                    is RepositoryRateLimitedException -> return RateLimited
                    is RepositoryRequestError -> return RequestError(e.localizedMessage, e.cause)
                }
            }
            return UnknownError(exceptions.firstOrNull())
        }

        fun fromException(e: Throwable): LoadError {
            return when (e) {
                is RepositoryAuthorizationException -> RequiresLogin
                is RepositoryNetworkException -> NetworkError
                is RepositoryServiceUnavailableException -> ServiceUnavailable
                is RepositoryRateLimitedException -> RateLimited
                is RepositoryRequestError -> RequestError(e.localizedMessage, e.cause)
                else -> UnknownError(e)
            }
        }

        inline fun runAndWrapOrThrowCancellation(block: () -> Unit): LoadError? {
            @Suppress("WRONG_INVOCATION_KIND")
            contract {
                callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            }

            return try {
                block()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                fromException(e)
            }
        }
    }
}
