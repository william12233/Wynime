package com.wynime.app.ui.subject.episode.details.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wynime.app.ui.foundation.rememberAsyncHandler
import com.wynime.app.ui.subject.collection.components.EditCollectionTypeDropDown
import com.wynime.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import com.wynime.datasources.api.topic.UnifiedCollectionType

@Composable
fun FavoriteIconButton(
    state: EditableSubjectCollectionTypeState,
    modifier: Modifier = Modifier,
) {
    val tasker = rememberAsyncHandler()
    val presentation by state.presentationFlow.collectAsStateWithLifecycle()

    var showEditCollectionTypeDropDown by rememberSaveable { mutableStateOf(false) }
    val collectionAtLeastWatching = when (presentation.selfCollectionType) {
        UnifiedCollectionType.DOING, UnifiedCollectionType.ON_HOLD, UnifiedCollectionType.DONE -> true
        else -> false
    }

    IconToggleButton(
        checked = collectionAtLeastWatching,
        onCheckedChange = {
            tasker.launch {
                if (collectionAtLeastWatching) {
                    showEditCollectionTypeDropDown = true
                } else {
                    state.setSelfCollectionType(UnifiedCollectionType.DOING)
                }
            }
        },
        modifier = modifier,
        enabled = !tasker.isWorking,
    ) {
        Icon(
            if (collectionAtLeastWatching) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = null,
        )
    }

    EditCollectionTypeDropDown(
        currentType = presentation.selfCollectionType,
        expanded = showEditCollectionTypeDropDown,
        onDismissRequest = { showEditCollectionTypeDropDown = false },
        onClick = {
            showEditCollectionTypeDropDown = false
            tasker.launch {
                state.setSelfCollectionType(it.type)
            }
        },
    )
}