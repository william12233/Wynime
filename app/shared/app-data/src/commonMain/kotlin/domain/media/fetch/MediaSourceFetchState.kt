package com.wynime.app.domain.media.fetch

import androidx.compose.runtime.Stable
import com.wynime.app.domain.mediasource.web.SolveRequest
import com.wynime.source.plugin.api.SourceDiagnostics

@Stable
sealed class MediaSourceFetchState {
    data object Idle : MediaSourceFetchState()

    data object Disabled : MediaSourceFetchState()

    data object Working : MediaSourceFetchState()

    sealed class Completed : MediaSourceFetchState() {
        internal abstract val id: Int
    }

    data class Succeed(
        override val id: Int,
    ) : Completed()

    data class NoMatch(
        val diagnostics: SourceDiagnostics,
        override val id: Int,
    ) : Completed()

    data class Failed(
        val cause: Throwable,
        override val id: Int,
        val diagnostics: SourceDiagnostics? = null,
    ) : Completed()

    data class CaptchaRequired(
        val request: SolveRequest,
        override val id: Int,
    ) : Completed()

    data class RateLimited(
        val retryAt: Long,
        override val id: Int,
    ) : Completed()

    data class Abandoned(
        val cause: Throwable, override val id: Int,
    ) : Completed()
}

val MediaSourceFetchState.isWorking get() = this is MediaSourceFetchState.Working
val MediaSourceFetchState.isDisabled get() = this is MediaSourceFetchState.Disabled
val MediaSourceFetchState.isFinal get() = this is MediaSourceFetchState.Completed || this is MediaSourceFetchState.Disabled
val MediaSourceFetchState.isFailedOrAbandoned get() = this is MediaSourceFetchState.Failed || this is MediaSourceFetchState.Abandoned
val MediaSourceFetchState.isCaptchaRequired get() = this is MediaSourceFetchState.CaptchaRequired
val MediaSourceFetchState.isRateLimited get() = this is MediaSourceFetchState.RateLimited
