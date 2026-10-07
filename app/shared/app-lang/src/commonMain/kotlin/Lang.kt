@file:Suppress("RemoveRedundantQualifierName")

package com.wynime.app.ui.lang

import androidx.compose.ui.text.intl.Locale

val Lang get() = com.wynime.app.ui.lang.Res.string

val LocaleZhCN = Locale("zh-CN")

val SupportedLocales = listOf(
    LocaleZhCN,
    Locale("zh-HK"),
    Locale("zh-TW"),
    Locale("en"),
)
