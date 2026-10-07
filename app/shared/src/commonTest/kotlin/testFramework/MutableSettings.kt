package com.wynime.app.testFramework

import kotlinx.coroutines.flow.MutableStateFlow
import com.wynime.app.data.repository.user.Settings

class MutableSettings<T>(
    initialValue: T
) : Settings<T> {
    override val flow: MutableStateFlow<T> = MutableStateFlow(initialValue)
    override suspend fun set(value: T) {
        flow.value = value
    }
}
