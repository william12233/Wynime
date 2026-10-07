package com.wynime.utils.ktor

import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom

object UrlHelpers {
    fun computeAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        require(baseUrl.isNotEmpty()) { "baseUrl must not be empty" }
        return URLBuilder(baseUrl).apply {
            if (relativeUrl.isNotBlank()) {
                encodedParameters = ParametersBuilder()
                encodedFragment = ""
            }
        }.takeFrom(relativeUrl).buildString()
    }

    fun computeAbsoluteUrlOrNull(baseUrl: String, relativeUrl: String): String? {
        return try {
            computeAbsoluteUrl(baseUrl, relativeUrl)
        } catch (_: Exception) {
            null
        }
    }
}
