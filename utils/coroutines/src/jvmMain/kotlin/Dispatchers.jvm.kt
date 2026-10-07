package com.wynime.utils.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val Dispatchers.IO_: CoroutineDispatcher
    get() = IO
