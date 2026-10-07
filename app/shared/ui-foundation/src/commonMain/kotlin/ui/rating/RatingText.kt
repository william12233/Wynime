@file:OptIn(TestOnly::class)

package com.wynime.app.ui.rating

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wynime.app.data.models.subject.RatingCounts
import com.wynime.app.data.models.subject.RatingInfo
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.text.ProvideTextStyleContentColor
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.rating_summary_multiline
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.utils.platform.format1f
import org.jetbrains.compose.resources.stringResource

@Composable
fun RatingText(
    rating: RatingInfo,
    modifier: Modifier = Modifier
) {
    Row(modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        ProvideTextStyleContentColor(
            MaterialTheme.typography.titleLarge,
            MaterialTheme.colorScheme.tertiary,
        ) {
            val text = remember(rating.score) {
                renderScore(rating.score)
            }
            Text(
                text,
                softWrap = false,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }

        Box(Modifier.padding(start = 4.dp).fillMaxHeight()) {
            ProvideTextStyleContentColor(
                MaterialTheme.typography.labelSmall,
                MaterialTheme.colorScheme.tertiary,
            ) {
                Column {
                    Text(
                        stringResource(Lang.rating_summary_multiline, rating.rank, rating.total),
                        maxLines = 2,
                        softWrap = false,
                    )
                }

            }
        }
    }
}

@Stable
fun renderScore(score: String): String {
    return if (!score.contains(".")) {
        "$score.0"
    } else {
        score.toFloatOrNull()?.let {
            String.format1f(it)
        } ?: score
    }
}

@TestOnly
private val TestRatingInfo = RatingInfo(
    rank = 123,
    total = 100,
    count = RatingCounts(IntArray(10) { it * 10 }),
    score = "6.7",
)

@Composable
@Preview
fun PreviewRatingText() {
    ProvideCompositionLocalsForPreview {
        Surface(Modifier.width(200.dp)) {
            RatingText(
                TestRatingInfo,
            )
        }
    }
}

@Composable
@Preview
fun PreviewRatingTextIntrinsicMin() {
    ProvideCompositionLocalsForPreview {
        Surface(Modifier.width(IntrinsicSize.Min)) {
            RatingText(
                TestRatingInfo,
            )
        }
    }
}
