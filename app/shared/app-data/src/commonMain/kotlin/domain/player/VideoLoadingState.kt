package com.wynime.app.domain.player

import androidx.compose.runtime.Immutable
import com.wynime.app.domain.media.resolver.MediaResolver
import com.wynime.app.domain.media.selector.MediaSelector
import com.wynime.datasources.api.Media
import com.wynime.source.plugin.api.SourceDiagnostics
import com.wynime.source.plugin.api.SourceResultStatus

@Immutable
sealed interface VideoLoadingState {
    sealed interface Progressing : VideoLoadingState

    data object Initial : VideoLoadingState

    data object ResolvingSource : VideoLoadingState, Progressing

    data object DecodingData : VideoLoadingState, Progressing

    data object Succeed : VideoLoadingState, Progressing

    sealed class Failed : VideoLoadingState
    data object ResolutionTimedOut : Failed()
    data object NetworkError : Failed()
    data object Cancelled : Failed()

    data object UnsupportedMedia : Failed()
    data object NoMatchingFile : Failed()
    data class SourceError(
        val status: SourceResultStatus,
        val diagnostics: SourceDiagnostics,
        val requiresVerification: Boolean,
        val retryable: Boolean,
    ) : Failed()
    data class UnknownError(
        val cause: Throwable,
    ) : Failed()
}
