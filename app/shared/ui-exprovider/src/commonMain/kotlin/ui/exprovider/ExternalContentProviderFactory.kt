package com.wynime.app.ui.exprovider

import kotlinx.coroutines.CoroutineScope
import com.wynime.app.platform.Context

interface ExternalContentProviderFactory {
    fun create(context: Context, coroutineScope: CoroutineScope): ExternalContentProvider?
}

object NoOpExternalContentProviderFactory : ExternalContentProviderFactory {
    override fun create(context: Context, coroutineScope: CoroutineScope): ExternalContentProvider? = null
}