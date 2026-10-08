package com.wynime.app.data.network

/** Converts user-entered traditional Chinese into the simplified form indexed by Bangumi. */
internal expect fun traditionalToSimplifiedChinese(text: String): String
