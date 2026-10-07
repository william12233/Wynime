package com.wynime.app.ui.foundation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.wynime.app.data.models.preference.SubjectAppearanceSettings

val LocalSubjectAppearanceSettings: ProvidableCompositionLocal<SubjectAppearanceSettings> =
    compositionLocalOf { SubjectAppearanceSettings.Default }
