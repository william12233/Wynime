package com.wynime.app.domain.mediasource.web.captcha

import com.wynime.app.domain.mediasource.web.BlockReason
import com.wynime.app.domain.mediasource.web.LoadedPage
import com.wynime.app.domain.mediasource.web.PageVerdict
import com.wynime.app.domain.mediasource.web.SolveRequest
import com.wynime.utils.ktor.ScopedHttpClient

sealed interface SolveOutcome {
    data object Solved : SolveOutcome

    data class Failed(val reason: BlockReason?) : SolveOutcome

    data object Cancelled : SolveOutcome

    data object Unsupported : SolveOutcome
}

interface CaptchaSolver {
    val id: String

    fun canAttempt(reason: BlockReason.Captcha, host: String): Boolean

    suspend fun attempt(ctx: SolveContext): SolveOutcome
}

class SolveContext internal constructor(
    val request: SolveRequest,

    val http: ScopedHttpClient,

    val acquireBrowser: suspend () -> CaptchaBrowser,

    val evaluate: suspend (LoadedPage) -> PageVerdict<*>,

    val retainSolvedPage: (LoadedPage) -> Unit = {},
)
