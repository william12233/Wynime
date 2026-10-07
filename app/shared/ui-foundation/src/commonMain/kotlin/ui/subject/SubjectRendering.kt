package com.wynime.app.ui.subject

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_rendering_season_year_month
import com.wynime.datasources.api.PackedDate
import com.wynime.datasources.api.seasonMonth
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Stable
@Composable
fun renderSubjectSeason(date: PackedDate): String {
    if (date == PackedDate.Invalid) return "TBA"
    if (date.seasonMonth == 0) {
        return date.toString()
    }
    return stringResource(Lang.subject_rendering_season_year_month, date.year.toString(), date.seasonMonth.toString())
}

suspend fun getSubjectSeasonText(date: PackedDate): String {
    if (date == PackedDate.Invalid) return "TBA"
    if (date.seasonMonth == 0) {
        return date.toString()
    }
    return getString(Lang.subject_rendering_season_year_month, date.year.toString(), date.seasonMonth.toString())
}
