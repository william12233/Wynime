/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** A small state-backed editor for a mutable configuration container. */
class SaveableStorage<Container : Any>(
    val containerState: State<Container?>,
    private val onSave: (Container) -> Unit,
    val isSavingFlow: Flow<Boolean>,
) {
    val container by containerState

    fun <P : Any> prop(
        get: (Container) -> P,
        copy: Container.(P) -> Container,
        default: P,
    ): ReadWriteProperty<Any?, P> = object : ReadWriteProperty<Any?, P> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): P {
            return container?.let(get) ?: default
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: P) {
            val current = container ?: return
            onSave(current.copy(value))
        }
    }

    fun <P> propNullable(
        get: (Container) -> P?,
        copy: Container.(P?) -> Container,
        default: P? = null,
    ): ReadWriteProperty<Any?, P?> = object : ReadWriteProperty<Any?, P?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): P? {
            return container?.let(get) ?: default
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: P?) {
            val current = container ?: return
            onSave(current.copy(value))
        }
    }

    fun set(container: Container) {
        onSave(container)
    }
}

@TestOnly
fun <T : Any> createTestSaveableStorage(
    initialValue: T?,
    isSaving: Boolean = false,
): SaveableStorage<T> {
    val containerState = mutableStateOf(initialValue)
    return SaveableStorage(
        containerState = containerState,
        onSave = { containerState.value = it },
        isSavingFlow = MutableStateFlow(isSaving),
    )
}
