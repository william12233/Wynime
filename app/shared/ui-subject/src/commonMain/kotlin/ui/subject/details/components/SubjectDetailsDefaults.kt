package com.wynime.app.ui.subject.details.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.OutlinedTag
import com.wynime.app.ui.subject.AiringLabel
import com.wynime.app.ui.subject.AiringLabelState
import com.wynime.app.ui.subject.renderSubjectSeason
import com.wynime.datasources.api.PackedDate

object SubjectDetailsDefaults {
    val TabRowWidth = 80.dp * 3
    val MaximumContentWidth = 1300.dp
}

@Suppress("UnusedReceiverParameter")
@Composable
fun SubjectDetailsDefaults.SeasonTag(
    airDate: PackedDate,
    airingLabelState: AiringLabelState,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        OutlinedTag { Text(renderSubjectSeason(airDate)) }
        AiringLabel(
            airingLabelState,
            Modifier.align(Alignment.CenterVertically),
            style = LocalTextStyle.current,
            progressColor = LocalContentColor.current,
        )
    }
}
