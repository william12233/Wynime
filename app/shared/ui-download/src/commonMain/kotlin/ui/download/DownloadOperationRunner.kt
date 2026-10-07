package com.wynime.app.ui.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.wynime.app.domain.media.download.DownloadOperation
import com.wynime.app.domain.media.download.DownloadOperations

class DownloadOperationRunner(
    private val operations: DownloadOperations,
    private val scope: CoroutineScope,
) {
    private val failures = MutableStateFlow(0)

    val failedCount: StateFlow<Int> = failures.asStateFlow()

    fun run(ids: Set<String>, operation: DownloadOperation) {
        if (ids.isEmpty()) return
        val pending = operations.submit(ids, operation)
        scope.launch {
            val count = pending.await().size
            if (count > 0) failures.update { it + count }
        }
    }

    fun dismissFailures() {
        failures.value = 0
    }
}
