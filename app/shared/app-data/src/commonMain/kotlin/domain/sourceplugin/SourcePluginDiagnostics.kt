/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.sourceplugin

import me.him188.ani.source.plugin.api.SourceDiagnostics
import me.him188.ani.source.plugin.api.SourceResultStatus

/**
 * A provider failure that has enough safe context for the media-source layer to classify it.
 *
 * The exception deliberately carries no response body, cookie value, token, or credential.
 */
class SourcePluginFailure(
    val status: SourceResultStatus,
    val diagnostics: SourceDiagnostics,
    val retryable: Boolean,
    val requiresVerification: Boolean = false,
    cause: Throwable? = null,
) : Exception(
    "Source plugin ${diagnostics.provider} failed with ${status.name} " +
        "at ${diagnostics.entryPoint}",
    cause,
)

/** A provider completed discovery without a safe subject or episode match. */
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
