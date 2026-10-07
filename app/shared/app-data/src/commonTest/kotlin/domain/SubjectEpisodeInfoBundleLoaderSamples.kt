package com.wynime.app.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.shareIn
import com.wynime.app.domain.episode.SubjectEpisodeInfoBundle
import com.wynime.app.domain.episode.SubjectEpisodeInfoBundleLoader
import com.wynime.test.Sample
import org.koin.core.Koin

@Sample
fun getBundleFlow(subjectId: Int, koin: Koin, backgroundScope: CoroutineScope) {
    val episodeIdFlow = MutableStateFlow(2)

    val loader = SubjectEpisodeInfoBundleLoader(subjectId, episodeIdFlow, koin)

    val bundleFlow: SharedFlow<SubjectEpisodeInfoBundle?> = loader.infoBundleFlow
        .shareIn(backgroundScope, started = WhileSubscribed(), replay = 1)
}
