package com.wynime.app.ui.subject.collection.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.wynime.app.ui.lang.*
import com.wynime.datasources.api.topic.UnifiedCollectionType
import org.jetbrains.compose.resources.*

@Immutable
class SubjectCollectionAction(
    val title: @Composable () -> Unit,
    val icon: @Composable () -> Unit,
    val type: UnifiedCollectionType,
)

@Immutable
object SubjectCollectionActions {
    @Stable
    val Wish = SubjectCollectionAction(
        { Text(stringResource(Lang.subject_collection_wish)) },
        { Icon(Icons.AutoMirrored.Rounded.EventNote, null) },
        UnifiedCollectionType.WISH,
    )

    @Stable
    val Doing = SubjectCollectionAction(
        { Text(stringResource(Lang.subject_collection_doing)) },
        { Icon(Icons.Rounded.PlayCircleOutline, null) },
        UnifiedCollectionType.DOING,
    )

    @Stable
    val Done = SubjectCollectionAction(
        { Text(stringResource(Lang.subject_collection_done)) },
        { Icon(Icons.Rounded.TaskAlt, null) },
        UnifiedCollectionType.DONE,
    )

    @Stable
    val OnHold = SubjectCollectionAction(
        { Text(stringResource(Lang.subject_collection_on_hold)) },
        { Icon(Icons.Rounded.AccessTime, null) },
        UnifiedCollectionType.ON_HOLD,
    )

    @Stable
    val Dropped = SubjectCollectionAction(
        { Text(stringResource(Lang.subject_collection_dropped)) },
        { Icon(Icons.Rounded.Block, null) },
        UnifiedCollectionType.DROPPED,
    )

    @Stable
    val DeleteCollection = SubjectCollectionAction(
        { Text(stringResource(Lang.subject_collection_delete), color = MaterialTheme.colorScheme.error) },
        { Icon(Icons.Rounded.DeleteOutline, null) },
        type = UnifiedCollectionType.NOT_COLLECTED,
    )

    @Stable
    val Collect = SubjectCollectionAction(
        { Text(stringResource(Lang.subject_collection_collect)) },
        { Icon(Icons.Rounded.Star, null) },
        type = UnifiedCollectionType.NOT_COLLECTED,
    )
}

private val SubjectCollectionActionsCommon
    get() = listOf(
        SubjectCollectionActions.Wish,
        SubjectCollectionActions.Doing,
        SubjectCollectionActions.Done,
        SubjectCollectionActions.OnHold,
        SubjectCollectionActions.Dropped,
    )

@Stable
val SubjectCollectionActionsForEdit = SubjectCollectionActionsCommon + listOf(
    SubjectCollectionActions.DeleteCollection,
)

@Stable
val SubjectCollectionActionsForCollect = SubjectCollectionActionsCommon + listOf(
    SubjectCollectionActions.Collect,
)
