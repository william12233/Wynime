package com.wynime.app.ui.foundation

import androidx.annotation.CallSuper
import androidx.compose.runtime.RememberObserver
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import com.wynime.utils.logging.error
import com.wynime.utils.logging.thisLogger
import com.wynime.utils.logging.trace
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

abstract class AbstractViewModel(
    backgroundCoroutineContext: CoroutineContext = EmptyCoroutineContext,
) : RememberObserver, ViewModel(), HasBackgroundScope {
    val logger by lazy { thisLogger() }

    private var _backgroundScope = createBackgroundScope(backgroundCoroutineContext)
    override val backgroundScope: CoroutineScope
        get() {
            return _backgroundScope
        }

    private var referenceCount = 0

    @CallSuper
    override fun onAbandoned() {
        referenceCount--
    }

    @CallSuper
    override fun onForgotten() {
        referenceCount--
    }

    @CallSuper
    @Suppress("DEPRECATION")
    override fun onRemembered() {
        referenceCount++
        logger.trace { "${this::class.simpleName} onRemembered, refCount=$referenceCount" }
        if (referenceCount == 1) {
            this.init()
        }
    }

    private fun createBackgroundScope(additionalContext: CoroutineContext): CoroutineScope {
        return CoroutineScope(
            additionalContext + CoroutineExceptionHandler { coroutineContext, throwable ->
                logger.error(throwable) { "Unhandled exception in background scope for viewmodel ${this::class.qualifiedName}, coroutineContext: $coroutineContext" }
            } + SupervisorJob(),
        )
    }

    @Deprecated(
        "只对 remember {} 创建的 ViewModel 生效; viewModel {} 取得的实例永远不会调用它, 请改用 Kotlin init {} 块",
        level = DeprecationLevel.WARNING,
    )
    protected open fun init() {
    }

    override fun onCleared() {
        backgroundScope.cancel()
        super.onCleared()
    }
}
