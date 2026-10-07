package androidx.paging.compose

import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.paging.PagingConfig
import kotlin.jvm.JvmSuppressWildcards

public fun <T : Any> LazyPagingItems<T>.itemKey(
    key: ((item: @JvmSuppressWildcards T) -> Any)? = null
): (index: Int) -> Any {
    return { index ->
        if (key == null) {
            getPagingPlaceholderKey(index)
        } else {
            val item = peek(index)
            if (item == null) getPagingPlaceholderKey(index) else key(item)
        }
    }
}

public fun <T : Any> LazyPagingItems<T>.itemContentType(
    contentType: ((item: @JvmSuppressWildcards T) -> Any?)? = null
): (index: Int) -> Any? {
    return { index ->
        if (contentType == null) {
            null
        } else {
            val item = peek(index)
            if (item == null) PagingPlaceholderContentType else contentType(item)
        }
    }
}