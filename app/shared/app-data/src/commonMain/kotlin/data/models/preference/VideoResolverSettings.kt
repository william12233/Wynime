package com.wynime.app.data.models.preference

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.wynime.app.data.models.preference.WebViewDriver.entries

@Serializable
@Immutable
data class VideoResolverSettings(
    val driver: WebViewDriver = WebViewDriver.AUTO,
    val headless: Boolean = true,
    val resourceExtractionTimeoutSeconds: Int = DEFAULT_RESOURCE_EXTRACTION_TIMEOUT_SECONDS,
    val dataSourceBrowserConcurrency: Int = DEFAULT_DATA_SOURCE_BROWSER_CONCURRENCY,

    @Suppress("PropertyName")
    @Transient val _placeholder: Int = 0,
) {
    val effectiveResourceExtractionTimeoutSeconds: Int
        get() = if (resourceExtractionTimeoutSeconds in ResourceExtractionTimeoutSecondsOptions) {
            resourceExtractionTimeoutSeconds
        } else {
            DEFAULT_RESOURCE_EXTRACTION_TIMEOUT_SECONDS
        }

    val effectiveResourceExtractionTimeoutMillis: Long
        get() = effectiveResourceExtractionTimeoutSeconds * 1_000L

    val effectiveDataSourceBrowserConcurrency: Int
        get() = if (dataSourceBrowserConcurrency in DataSourceBrowserConcurrencyOptions) {
            dataSourceBrowserConcurrency
        } else {
            DEFAULT_DATA_SOURCE_BROWSER_CONCURRENCY
        }

    companion object {
        const val DEFAULT_RESOURCE_EXTRACTION_TIMEOUT_SECONDS = 8
        val ResourceExtractionTimeoutSecondsOptions = listOf(3, 5, 8, 10, 15, 20, 30)
        const val DEFAULT_DATA_SOURCE_BROWSER_CONCURRENCY = 8
        val DataSourceBrowserConcurrencyOptions = listOf(1, 2, 4, 8, 12, 16, 24, 32)

        val Default = VideoResolverSettings()
    }
}

@Serializable
enum class WebViewDriver {
    CHROME,
    EDGE,
    AUTO;

    override fun toString(): String {
        return this.name.lowercase()
    }

    companion object {
        val enabledEntries by lazy {
            entries.sortedDescending()
        }
    }
}
