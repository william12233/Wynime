package com.wynime.utils.selectorworkflow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Modifier
import com.wynime.utils.selectorworkflow.draw.WorkflowLayout
import com.wynime.utils.selectorworkflow.draw.WorkflowMetrics
import com.wynime.utils.selectorworkflow.draw.WorkflowPalette
import com.wynime.utils.selectorworkflow.draw.drawSelectorWorkflow
import com.wynime.utils.selectorworkflow.draw.rememberWorkflowPalette

@Composable
fun SelectorWorkflowAnimation(
    viewModel: SelectorWorkflowViewModel,
    modifier: Modifier = Modifier,
    palette: WorkflowPalette = rememberWorkflowPalette(),
    metrics: WorkflowMetrics = WorkflowMetrics.Default,
    readoutStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
    LaunchedEffect(viewModel) {
        while (true) {
            withFrameNanos { viewModel.onFrame(it) }
        }
    }
    SelectorWorkflowAnimation(
        state = viewModel.state,
        config = viewModel.config,
        modifier = modifier,
        palette = palette,
        metrics = metrics,
        readoutStyle = readoutStyle,
    )
}

@Composable
fun SelectorWorkflowAnimation(
    state: SelectorWorkflowState,
    config: SelectorWorkflowConfig,
    modifier: Modifier = Modifier,
    palette: WorkflowPalette = rememberWorkflowPalette(),
    metrics: WorkflowMetrics = WorkflowMetrics.Default,
    readoutStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
    val layout = remember(config, metrics) { WorkflowLayout.of(config, metrics) }
    val textMeasurer = rememberTextMeasurer()
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(layout.canvasSize.width / layout.canvasSize.height),
    ) {
        drawSelectorWorkflow(state, layout, palette, textMeasurer, readoutStyle)
    }
}
