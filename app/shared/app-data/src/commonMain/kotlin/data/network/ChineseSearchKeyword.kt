package com.wynime.app.data.network

/** Converts user-entered traditional Chinese into the simplified form indexed by Bangumi. */
internal expect fun traditionalToSimplifiedChinese(text: String): String

/**
 * Text normalization is an optional search enhancement. A missing or broken platform converter
 * must leave the original query usable because providers may index either Chinese form.
 */
internal fun simplifyChineseOrOriginal(text: String): String = simplifyChineseOrOriginal(
    text = text,
    converter = ::traditionalToSimplifiedChinese,
)

internal fun simplifyChineseOrOriginal(
    text: String,
    converter: (String) -> String,
): String = try {
    converter(text).takeIf { it.isNotBlank() } ?: text
} catch (_: Exception) {
    text
} catch (_: LinkageError) {
    text
}
