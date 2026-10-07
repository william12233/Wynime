package com.wynime.app.ui.exprovider

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.SharedFlow
import com.wynime.app.platform.Context

interface ExternalContentProvider {
    val events: SharedFlow<ExternalContentEvent>

    fun viewProvider(context: Context, contentId: String, expectedWidth: Int, expectedHeight: Int): Any

    suspend fun initialize(context: Context, contentId: String)

    fun dispose(contentId: String)
}

sealed interface ExternalContentEvent {
    data class Shown(val contentId: String) : ExternalContentEvent
    data class LoadFailed(val contentId: String) : ExternalContentEvent
    data class Clicked(val contentId: String) : ExternalContentEvent
    data class Closed(val contentId: String) : ExternalContentEvent
}

val LocalExternalContentProvider = staticCompositionLocalOf<ExternalContentProvider?> {
    null
}