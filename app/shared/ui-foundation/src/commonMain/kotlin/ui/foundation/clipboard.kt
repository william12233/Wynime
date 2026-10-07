package com.wynime.app.ui.foundation

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

expect fun textClipEntryOf(text: String): ClipEntry

suspend inline fun Clipboard.setClipEntryText(text: String) = setClipEntry(textClipEntryOf(text))
expect suspend fun Clipboard.getClipEntryText(): String?
