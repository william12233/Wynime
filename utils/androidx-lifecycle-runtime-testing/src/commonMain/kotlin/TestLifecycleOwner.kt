@file:Suppress("MemberVisibilityCanBePrivate")

package androidx.lifecycle.testing

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.jvm.JvmOverloads

public class TestLifecycleOwner
@JvmOverloads
constructor(
    initialState: Lifecycle.State = Lifecycle.State.STARTED,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : LifecycleOwner {

    @Suppress("VisibleForTests")
    private val lifecycleRegistry =
        LifecycleRegistry.createUnsafe(this).apply { currentState = initialState }

    override val lifecycle: LifecycleRegistry
        get() = lifecycleRegistry

    public fun handleLifecycleEvent(event: Lifecycle.Event) {
        runBlocking(coroutineDispatcher) { lifecycleRegistry.handleLifecycleEvent(event) }
    }

    public var currentState: Lifecycle.State
        get() = runBlocking(coroutineDispatcher) { lifecycleRegistry.currentState }
        set(value) {
            runBlocking(coroutineDispatcher) { lifecycleRegistry.currentState = value }
        }

    public suspend fun setCurrentState(state: Lifecycle.State) {
        withContext(coroutineDispatcher) { lifecycleRegistry.currentState = state }
    }

    public val observerCount: Int
        get() = lifecycleRegistry.observerCount
}