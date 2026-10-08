package com.wynime.app.domain.sourceplugin

import kotlinx.coroutines.CancellationException
import com.wynime.app.domain.mediasource.web.SolveRequest
import com.wynime.source.plugin.api.SourceDiagnostics
import com.wynime.source.plugin.api.SourceResultStatus

class SourcePluginFailure(
    val status: SourceResultStatus,
    val diagnostics: SourceDiagnostics,
    val retryable: Boolean,
    val requiresVerification: Boolean = false,
    val verificationRequest: SolveRequest? = null,
    cause: Throwable? = null,
) : Exception(
    "Source plugin ${diagnostics.provider} failed with ${status.name} " +
        "at ${diagnostics.entryPoint}",
    cause,
)

class SourcePluginNoMatchException(
    val diagnostics: SourceDiagnostics,
) : Exception(
    "Source plugin ${diagnostics.provider} returned no safe match",
)

internal fun safeSourceUrl(url: String): String {
    val withoutQuery = url.substringBefore('#').substringBefore('?')
    val schemeSeparator = withoutQuery.indexOf("://")
    if (schemeSeparator < 0) return withoutQuery.ifBlank { url }

    val prefix = withoutQuery.substring(0, schemeSeparator + 3)
    val remainder = withoutQuery.substring(schemeSeparator + 3)
    val pathStart = remainder.indexOf('/')
    val authority = if (pathStart < 0) remainder else remainder.substring(0, pathStart)
    val path = if (pathStart < 0) "" else remainder.substring(pathStart)
    return prefix + authority.substringAfterLast('@') + path
}

internal fun sourceDomain(url: String): String? {
    val authority = url.substringAfter("://", "").substringBefore('/')
    return authority.substringAfterLast('@').substringBefore(':').takeIf(String::isNotBlank)
}

internal fun sourceFailureDiagnostics(
    traceId: String,
    provider: String,
    entryPoint: String,
    status: SourceResultStatus,
    url: String? = null,
    statusCode: Int? = null,
    contentType: String? = null,
    redirectCount: Int? = null,
    elapsedMillis: Long? = null,
    refererPresent: Boolean? = null,
    challengeDetected: String? = null,
    failureReason: String? = null,
): SourceDiagnostics = SourceDiagnostics(
    traceId = traceId,
    provider = provider,
    entryPoint = entryPoint,
    url = url?.let(::safeSourceUrl),
    domain = url?.let(::sourceDomain),
    statusCode = statusCode,
    contentType = contentType,
    redirectCount = redirectCount,
    elapsedMillis = elapsedMillis,
    responseCategory = status,
    userAgentProfile = "BROWSER",
    refererPresent = refererPresent,
    challengeDetected = challengeDetected,
    failureReason = failureReason,
)

internal fun sourcePluginBoundaryFailure(
    traceId: String,
    provider: String,
    entryPoint: String,
    fallbackStatus: SourceResultStatus,
    error: Throwable,
    url: String? = null,
    retryable: Boolean,
): SourcePluginFailure {
    if (error is CancellationException) throw error
    if (error is Error && error !is LinkageError) throw error
    val status = if (error is LinkageError || error is ClassCastException) {
        SourceResultStatus.PLUGIN_ERROR
    } else {
        fallbackStatus
    }
    return SourcePluginFailure(
        status = status,
        diagnostics = sourceFailureDiagnostics(
            traceId = traceId,
            provider = provider,
            entryPoint = entryPoint,
            status = status,
            url = url,
            failureReason = error::class.simpleName,
        ),
        retryable = retryable && status != SourceResultStatus.PLUGIN_ERROR,
        cause = error,
    )
}
