package com.wynime.app.ui.foundation.saveable

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.structuralEqualityPolicy

fun <Content, Saveable : Any> mutableStateSaver(
    contentSaver: Saver<Content, Saveable>,
    policy: SnapshotMutationPolicy<Content> = structuralEqualityPolicy()
): Saver<MutableState<Content>, Saveable> {
    return Saver(
        save = { state ->
            with(contentSaver) {
                save(state.value)
            }
        },
        restore = { saveable ->
            contentSaver.restore(saveable)?.let {
                mutableStateOf(it, policy)
            }
        },
    )
}
