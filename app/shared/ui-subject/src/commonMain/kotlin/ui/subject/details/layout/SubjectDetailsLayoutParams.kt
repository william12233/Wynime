package com.wynime.app.ui.subject.details.layout

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

enum class SubjectDetailsPaneKind {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

@Immutable
data class SubjectDetailsLayoutParams(
    val kind: SubjectDetailsPaneKind,

    val sidebarWidth: Dp,

    val sidebarItemSpacing: Dp,

    val railWidth: Dp,

    val railItemSpacing: Dp,

    val columnSpacing: Dp,

    val contentHorizontalPadding: Dp,

    val contentTopPadding: Dp,
    val contentBottomPadding: Dp,

    val sectionSpacing: Dp,

    val showInlineRatingHistogram: Boolean = false,

    val staffGridColumns: Int = 6,

    val showCacheButtonLabel: Boolean = true,
) {
    val isMultiColumn: Boolean get() = kind != SubjectDetailsPaneKind.COMPACT
    val showRail: Boolean get() = kind == SubjectDetailsPaneKind.EXPANDED

    @Stable
    companion object {
        val Compact = SubjectDetailsLayoutParams(
            kind = SubjectDetailsPaneKind.COMPACT,
            sidebarWidth = 0.dp,
            sidebarItemSpacing = 0.dp,
            railWidth = 0.dp,
            railItemSpacing = 0.dp,
            columnSpacing = 0.dp,
            contentHorizontalPadding = 16.dp,
            contentTopPadding = 16.dp,
            contentBottomPadding = 16.dp,
            sectionSpacing = 20.dp,
        )

        val Medium = SubjectDetailsLayoutParams(
            kind = SubjectDetailsPaneKind.MEDIUM,
            sidebarWidth = 340.dp,
            sidebarItemSpacing = 20.dp,
            railWidth = 0.dp,
            railItemSpacing = 0.dp,
            columnSpacing = 48.dp,
            contentHorizontalPadding = 40.dp,
            contentTopPadding = 12.dp,
            contentBottomPadding = 40.dp,
            sectionSpacing = 28.dp,
            showInlineRatingHistogram = true,
        )

        val MediumNarrow = SubjectDetailsLayoutParams(
            kind = SubjectDetailsPaneKind.MEDIUM,
            sidebarWidth = 280.dp,
            sidebarItemSpacing = 20.dp,
            railWidth = 0.dp,
            railItemSpacing = 0.dp,
            columnSpacing = 24.dp,
            contentHorizontalPadding = 24.dp,
            contentTopPadding = 12.dp,
            contentBottomPadding = 24.dp,
            sectionSpacing = 24.dp,
            staffGridColumns = 3,
            showCacheButtonLabel = false,
        )

        val Expanded = SubjectDetailsLayoutParams(
            kind = SubjectDetailsPaneKind.EXPANDED,
            sidebarWidth = 340.dp,
            sidebarItemSpacing = 20.dp,
            railWidth = 372.dp,
            railItemSpacing = 20.dp,
            columnSpacing = 48.dp,
            contentHorizontalPadding = 48.dp,
            contentTopPadding = 12.dp,
            contentBottomPadding = 48.dp,
            sectionSpacing = 28.dp,
        )

        val EXPANDED_WIDTH_DP = WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND

        val MEDIUM_WIDTH_DP = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND

        const val MEDIUM_WIDE_WIDTH_DP = 1000

        fun calculate(availableWidth: Dp): SubjectDetailsLayoutParams = when {
            availableWidth >= EXPANDED_WIDTH_DP.dp -> Expanded
            availableWidth >= MEDIUM_WIDE_WIDTH_DP.dp -> Medium
            availableWidth >= MEDIUM_WIDTH_DP.dp -> MediumNarrow
            else -> Compact
        }
    }
}
