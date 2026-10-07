package com.wynime.app.tools.paging

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState

fun CombinedLoadStates.exceptions(): Sequence<Throwable> {
    return sequence {
        (mediator?.append as? LoadState.Error)?.error?.let { yield(it) }
        (mediator?.prepend as? LoadState.Error)?.error?.let { yield(it) }
        (mediator?.refresh as? LoadState.Error)?.error?.let { yield(it) }
        (source.append as? LoadState.Error)?.error?.let { yield(it) }
        (source.prepend as? LoadState.Error)?.error?.let { yield(it) }
        (source.refresh as? LoadState.Error)?.error?.let { yield(it) }
    }
}
