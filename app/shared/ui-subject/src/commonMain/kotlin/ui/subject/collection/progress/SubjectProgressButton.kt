package com.wynime.app.ui.subject.collection.progress

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wynime.app.ui.subject.SubjectProgressState
import com.wynime.app.ui.subject.rememberSubjectStatusStrings

@Composable
fun SubjectProgressButton(
    state: SubjectProgressState,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val requiredWidth = Modifier.requiredWidth(IntrinsicSize.Max)
    val strings = rememberSubjectStatusStrings()
    Crossfade(state.buttonIsPrimary) { isPrimary ->
        if (isPrimary) {
            Button(onClick = onPlay, modifier) {
                Text(state.buttonText(strings), requiredWidth, softWrap = false)
            }
        } else {
            FilledTonalButton(onClick = onPlay, modifier) {
                Text(state.buttonText(strings), requiredWidth, softWrap = false)
            }
        }
    }
}
