package com.wynime.app.ui.settings.framework

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import com.wynime.app.data.repository.user.Settings
import com.wynime.app.tools.MonoTasker
import com.wynime.app.ui.foundation.produceState
import com.wynime.utils.platform.annotations.TestOnly

fun <Value : Placeholder, Placeholder> Settings<Value>.stateIn(
    backgroundScope: CoroutineScope,
    placeholder: Placeholder,
): BaseSettingsState<Value, Placeholder> {
    return BaseSettingsState(
        flow.produceState(placeholder, backgroundScope),
        onUpdate = { set(it) },
        placeholder,
        backgroundScope,
    )
}

typealias SettingsState<T> = BaseSettingsState<T, T>

@Stable
class BaseSettingsState<in Value : Placeholder, out Placeholder>(
    valueState: State<Placeholder>,
    private val onUpdate: suspend (Value) -> Unit,
    private val placeholder: Placeholder,
    backgroundScope: CoroutineScope,
) : State<Placeholder> {
    private val tasker = MonoTasker(backgroundScope)
    fun update(value: Value) {
        tasker.launch {
            onUpdate(value)
        }
    }

    suspend fun updateSuspended(value: Value) {
        tasker.launch {
            onUpdate(value)
        }.join()
    }

    override val value: Placeholder by valueState
    val isLoading by derivedStateOf { value === placeholder }
    val isUpdating get() = tasker.isRunning
}

@TestOnly
fun <T> createTestSettingsState(value: T, backgroundScope: CoroutineScope): SettingsState<T> {
    val state = mutableStateOf(value)
    return SettingsState(state, onUpdate = { state.value = it }, value, backgroundScope)
}

@TestOnly
@Composable
fun <T> rememberTestSettingsState(value: T): SettingsState<T> {
    val scope = rememberCoroutineScope()
    return remember { createTestSettingsState(value, scope) }
}
