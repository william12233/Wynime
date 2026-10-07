@file:Suppress("NOTHING_TO_INLINE")

package com.wynime.app.ui.foundation

import androidx.compose.ui.Modifier

inline fun Modifier.thenNotNull(
    modifier: Modifier?
): Modifier {
    return if (modifier == null) this else this.then(modifier)
}

@OverloadResolutionByLambdaReturnType
inline fun Modifier.ifThen(
    condition: Boolean,
    modifier: Modifier.Companion.() -> Modifier?
): Modifier {
    return if (condition) this.then(modifier(Modifier.Companion) ?: Modifier) else this
}

@OverloadResolutionByLambdaReturnType
inline fun <T> Modifier.ifNotNullThen(
    value: T,
    modifier: Modifier.Companion.(T & Any) -> Modifier?
): Modifier {
    return if (value != null) this.then(modifier(Modifier.Companion, value) ?: Modifier) else this
}
