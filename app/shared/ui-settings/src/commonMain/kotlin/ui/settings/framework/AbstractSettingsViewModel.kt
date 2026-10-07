package com.wynime.app.ui.settings.framework

import kotlinx.coroutines.CoroutineScope
import com.wynime.app.data.repository.user.Settings
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.app.ui.foundation.produceState
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

abstract class AbstractSettingsViewModel : AbstractViewModel() {

    fun <Value : Placeholder, Placeholder> Settings<Value>.stateInBackground(
        placeholder: Placeholder,
        backgroundScope: CoroutineScope = this@AbstractSettingsViewModel.backgroundScope,
    ): BaseSettingsState<Value, Placeholder> {
        return BaseSettingsState(
            flow.produceState(placeholder, backgroundScope),
            onUpdate = { set(it) },
            placeholder,
            backgroundScope,
        )
    }

    private inline fun <T> propertyDelegateProvider(
        crossinline createProperty: (property: KProperty<*>) -> T,
    ): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, T>> {
        return PropertyDelegateProvider { _, property ->
            val value = createProperty(property)
            ReadOnlyProperty { _, _ ->
                value
            }
        }
    }

    @Deprecated(
        "Use stateInBackground instead",
        ReplaceWith("settings.stateInBackground(placeholder)"),
    )
    fun <Value : Placeholder, Placeholder> settings(
        settings: Settings<Value>,
        placeholder: Placeholder
    ): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, BaseSettingsState<Value, Placeholder>>> {
        return propertyDelegateProvider {
            settings.stateInBackground(placeholder)
        }
    }
}