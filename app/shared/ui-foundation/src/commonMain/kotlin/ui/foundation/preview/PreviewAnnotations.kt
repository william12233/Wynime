package com.wynime.app.ui.foundation.preview

import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Compact")
@Preview(
    name = "Medium",
    device = "spec:width=800dp,height=800dp,dpi=240",
)
@Preview(
    name = "Expanded",
    device = "spec:width=1200dp,height=800dp,dpi=240",
)
annotation class PreviewSizeClasses
