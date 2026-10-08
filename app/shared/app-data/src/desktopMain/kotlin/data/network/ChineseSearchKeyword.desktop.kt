package com.wynime.app.data.network

import com.github.houbb.opencc4j.util.ZhConverterUtil

internal actual fun traditionalToSimplifiedChinese(text: String): String =
    ZhConverterUtil.toSimple(text)
