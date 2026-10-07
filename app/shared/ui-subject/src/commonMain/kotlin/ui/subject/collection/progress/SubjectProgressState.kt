package com.wynime.app.ui.subject.collection.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import com.wynime.app.data.models.subject.SubjectCollectionInfo
import com.wynime.app.data.models.subject.SubjectProgressInfo
import com.wynime.app.data.repository.episode.EpisodeProgressRepository
import com.wynime.app.ui.foundation.stateOf
import com.wynime.app.ui.subject.SubjectProgressState
import com.wynime.datasources.api.PackedDate
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.coroutines.CoroutineContext

@Stable
class SubjectProgressStateFactory(
    private val episodeProgressRepository: EpisodeProgressRepository,
    private val flowCoroutineContext: CoroutineContext = Dispatchers.Default,
    val getCurrentDate: () -> PackedDate = { PackedDate.now() },
) {

}

@Composable
fun SubjectProgressStateFactory.rememberSubjectProgressState(
    subjectCollection: SubjectCollectionInfo,
): SubjectProgressState {
    val subjectId: Int = subjectCollection.subjectId
    val subjectCollectionState by rememberUpdatedState(subjectCollection)

    val info = remember {
        derivedStateOf {
            SubjectProgressInfo.compute(
                subjectCollectionState.subjectInfo,
                subjectCollectionState.episodes,
                getCurrentDate(),
                recurrence = subjectCollection.recurrence,
            )
        }
    }

    return remember(info, this, subjectId) {
        SubjectProgressState(
            info,
        )
    }
}

@Composable
@TestOnly
fun rememberTestSubjectProgressState(
    info: SubjectProgressInfo = SubjectProgressInfo.Done,
): SubjectProgressState {
    return remember {
        createTestSubjectProgressState(info)
    }
}

@TestOnly
fun createTestSubjectProgressState(info: SubjectProgressInfo = SubjectProgressInfo.Done) =
    SubjectProgressState(
        info = stateOf(info),
    )
