package com.wynime.app.ui.mediafetch.request

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.IconButton
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.mediafetch_request_editor_add_name
import com.wynime.app.ui.lang.mediafetch_request_editor_collapse
import com.wynime.app.ui.lang.mediafetch_request_editor_delete_name
import com.wynime.app.ui.lang.mediafetch_request_editor_episode_ep
import com.wynime.app.ui.lang.mediafetch_request_editor_episode_ep_supporting
import com.wynime.app.ui.lang.mediafetch_request_editor_episode_info
import com.wynime.app.ui.lang.mediafetch_request_editor_episode_info_supporting
import com.wynime.app.ui.lang.mediafetch_request_editor_episode_sort
import com.wynime.app.ui.lang.mediafetch_request_editor_episode_sort_supporting
import com.wynime.app.ui.lang.mediafetch_request_editor_expand
import com.wynime.app.ui.lang.mediafetch_request_editor_primary_name
import com.wynime.app.ui.lang.mediafetch_request_editor_primary_name_supporting
import com.wynime.app.ui.lang.mediafetch_request_editor_secondary_names
import com.wynime.app.ui.lang.mediafetch_request_editor_secondary_names_supporting
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@Composable
fun MediaFetchRequestEditor(
    fetchRequest: EditingMediaFetchRequest,
    onFetchRequestChange: (EditingMediaFetchRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val primaryNameText = stringResource(Lang.mediafetch_request_editor_primary_name)
    val primaryNameSupportingText = stringResource(Lang.mediafetch_request_editor_primary_name_supporting)
    val secondaryNamesText = stringResource(Lang.mediafetch_request_editor_secondary_names)
    val secondaryNamesSupportingText = stringResource(Lang.mediafetch_request_editor_secondary_names_supporting)
    val collapseText = stringResource(Lang.mediafetch_request_editor_collapse)
    val expandText = stringResource(Lang.mediafetch_request_editor_expand)
    val addNameText = stringResource(Lang.mediafetch_request_editor_add_name)
    val episodeInfoText = stringResource(Lang.mediafetch_request_editor_episode_info)
    val episodeInfoSupportingText = stringResource(Lang.mediafetch_request_editor_episode_info_supporting)
    val episodeSortText = stringResource(Lang.mediafetch_request_editor_episode_sort)
    val episodeSortSupportingText = stringResource(Lang.mediafetch_request_editor_episode_sort_supporting)
    val episodeEpText = stringResource(Lang.mediafetch_request_editor_episode_ep)
    val episodeEpSupportingText = stringResource(Lang.mediafetch_request_editor_episode_ep_supporting)

    val listItemColors = ListItemDefaults.colors(
        containerColor = Color.Transparent,
    )
    Column(
        modifier = modifier
            .verticalScroll(scrollState),
    ) {
        val horizontalPadding = 16.dp
        val verticalSpacing = 16.dp

        OutlinedTextField(
            value = fetchRequest.primaryName,
            onValueChange = { onFetchRequestChange(fetchRequest.copy(primaryName = it)) },
            label = { Text(primaryNameText) },
            supportingText = { Text(primaryNameSupportingText) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
            singleLine = true,
        )

        var showComplementaryNames by rememberSaveable { mutableStateOf(false) }
        ListItem(
            headlineContent = {
                Text(
                    secondaryNamesText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            Modifier.padding(top = 8.dp),
            supportingContent = {
                Text(secondaryNamesSupportingText)
            },
            colors = listItemColors,
            trailingContent = {
                IconToggleButton(
                    showComplementaryNames,
                    onCheckedChange = { showComplementaryNames = it },
                ) {

                    if (showComplementaryNames) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = collapseText)
                    } else {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = expandText)
                    }
                }
            },
        )

        WynimeAnimatedVisibility(showComplementaryNames) {
            Column {
                Column(
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                ) {
                    fetchRequest.complementaryNames.forEachIndexed { index, name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { newName ->
                                    val updated =
                                        fetchRequest.complementaryNames.toMutableList().apply { this[index] = newName }
                                    onFetchRequestChange(fetchRequest.copy(complementaryNames = updated))
                                },
                                modifier = Modifier.weight(1f).padding(start = horizontalPadding),
                                singleLine = true,
                                label = { Text("Name #${index + 1}") },
                            )
                            IconButton(
                                onClick = {
                                    val updated =
                                        fetchRequest.complementaryNames.toMutableList().also { it.removeAt(index) }
                                    onFetchRequestChange(fetchRequest.copy(complementaryNames = updated))
                                },
                                Modifier.padding(end = horizontalPadding - 8.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(
                                        Lang.mediafetch_request_editor_delete_name,
                                        index + 1,
                                    ),
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = {
                        val updated = fetchRequest.complementaryNames + ""
                        onFetchRequestChange(fetchRequest.copy(complementaryNames = updated))
                    },
                    Modifier.align(Alignment.End)
                        .padding(top = verticalSpacing - 8.dp)
                        .padding(horizontal = horizontalPadding),
                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(addNameText)
                }
            }
        }

        ListItem(
            headlineContent = {
                Text(
                    episodeInfoText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            Modifier.padding(vertical = 8.dp),
            supportingContent = {
                Text(
                    episodeInfoSupportingText,
                )
            },
            colors = listItemColors,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {

            val sortAndEpAreError = fetchRequest.episodeSort.isEmpty() && fetchRequest.episodeEp.isEmpty()
            OutlinedTextField(
                value = fetchRequest.episodeSort,
                onValueChange = { newValue ->
                    onFetchRequestChange(fetchRequest.copy(episodeSort = newValue))
                },
                label = { Text(episodeSortText) },
                supportingText = { Text(episodeSortSupportingText) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
                singleLine = true,
                isError = sortAndEpAreError,
            )
            OutlinedTextField(
                value = fetchRequest.episodeEp,
                onValueChange = { newValue ->
                    onFetchRequestChange(fetchRequest.copy(episodeEp = newValue))
                },
                label = { Text(episodeEpText) },
                supportingText = { Text(episodeEpSupportingText) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
                singleLine = true,
                isError = sortAndEpAreError,
            )
        }
    }
}

@OptIn(TestOnly::class)
@Preview(showBackground = true, locale = "zh-rCN")
@Composable
private fun MediaFetchRequestEditorPreview() {
    var state by remember { mutableStateOf(TestEditingMediaFetchRequest) }

    ProvideCompositionLocalsForPreview {
        MediaFetchRequestEditor(fetchRequest = state, onFetchRequestChange = { state = it })
    }
}
