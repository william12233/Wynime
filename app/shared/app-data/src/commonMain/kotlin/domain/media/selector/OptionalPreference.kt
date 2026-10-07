package com.wynime.app.domain.media.selector

import androidx.compose.runtime.Stable
import com.wynime.utils.coroutines.Symbol
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmInline

@Stable
@JvmInline
value class OptionalPreference<@Suppress("unused") T : Any> private constructor(
    @PublishedApi internal val rawValue: Any
) {

    val isPreferValue: Boolean get() = rawValue !== PREFER_NO_VALUE && rawValue !== NO_PREFERENCE

    val isPreferNoValue: Boolean get() = rawValue === PREFER_NO_VALUE

    val hasPreference: Boolean get() = rawValue !== NO_PREFERENCE

    val hasNoPreference: Boolean get() = rawValue === NO_PREFERENCE

    companion object {
        fun <T : Any> prefer(value: T): OptionalPreference<T> = OptionalPreference(value)
        fun <T : Any> preferIfNotNull(value: T?): OptionalPreference<T> =
            if (value == null) noPreference() else prefer(value)

        fun <T : Any> preferNoValue(): OptionalPreference<T> = OptionalPreference(PREFER_NO_VALUE)
        fun <T : Any> noPreference(): OptionalPreference<T> = OptionalPreference(NO_PREFERENCE)
    }
}

private val PREFER_NO_VALUE = Symbol("PREFER_NO_VALUE")
private val NO_PREFERENCE = Symbol("NO_PREFERENCE")

inline val <T : Any> OptionalPreference<T>.preferredValueOrNull: T?
    get() =
        @Suppress("UNCHECKED_CAST")
        if (isPreferValue) rawValue as T else null

inline val <T : Any> OptionalPreference<T>.preferredValueOrFail: T
    get() =
        @Suppress("UNCHECKED_CAST")
        if (isPreferValue) rawValue as T else throw IllegalStateException("No value is preferred")

inline fun <T : R, R> OptionalPreference<T & Any>.orElse(default: () -> R): R? {
    contract { callsInPlace(default, InvocationKind.AT_MOST_ONCE) }
    return when {
        isPreferNoValue -> null
        isPreferValue -> return preferredValueOrNull
        else -> return default()
    }
}

inline fun <T : Any> OptionalPreference<T>.flatMapNoPreference(
    default: () -> OptionalPreference<T>
): OptionalPreference<T> {
    contract { callsInPlace(default, InvocationKind.AT_MOST_ONCE) }
    if (this.hasNoPreference) {
        return default()
    }
    return this
}
