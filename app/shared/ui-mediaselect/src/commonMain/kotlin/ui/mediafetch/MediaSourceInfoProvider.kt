package com.wynime.app.ui.mediafetch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.wynime.datasources.api.source.MediaSourceInfo
import com.wynime.utils.platform.annotations.TestOnly

@Stable
class MediaSourceInfoProvider(
    val getSourceInfoFlow: (mediaSourceId: String) -> Flow<MediaSourceInfo?>,
) {
    @Composable
    fun rememberMediaSourceInfo(mediaSourceId: String): State<MediaSourceInfo?> {
        return remember(mediaSourceId) {
            getSourceInfoFlow(mediaSourceId)
        }.collectAsStateWithLifecycle(null)
    }
}

@Composable
@TestOnly
fun rememberTestMediaSourceInfoProvider(): MediaSourceInfoProvider {
    return remember {
        createTestMediaSourceInfoProvider()
    }
}

@TestOnly
fun createTestMediaSourceInfoProvider(): MediaSourceInfoProvider {
    return MediaSourceInfoProvider(
        getSourceInfoFlow = {
            flowOf(MediaSourceInfo(displayName = it))
        },
    )
}