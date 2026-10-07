@file:Suppress("UnusedReceiverParameter")

package com.wynime.app.ui.subject.details.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wynime.app.data.models.subject.SubjectCollectionStats
import com.wynime.app.ui.foundation.theme.slightlyWeaken
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_details_collection_dropped
import com.wynime.app.ui.lang.subject_details_collection_summary
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubjectDetailsDefaults.CollectionData(
    collectionStats: SubjectCollectionStats,
    modifier: Modifier = Modifier,
) {

    Row(modifier) {
        val collection = collectionStats
        Text(
            stringResource(
                Lang.subject_details_collection_summary,
                collection.collect.toString(),
                collection.doing.toString(),
            ),
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            stringResource(Lang.subject_details_collection_dropped, collection.dropped.toString()),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            color = LocalContentColor.current.slightlyWeaken(),
        )
    }
}
