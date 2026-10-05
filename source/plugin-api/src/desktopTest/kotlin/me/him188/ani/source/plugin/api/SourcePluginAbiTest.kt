/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.source.plugin.api

import kotlin.jvm.internal.DefaultConstructorMarker
import kotlin.test.Test
import kotlin.test.assertNotNull

class SourcePluginAbiTest {
    @Test
    fun legacySourceHttpRequestConstructorsAreEmitted() {
        assertLegacyConstructors(
            SourceHttpRequest::class.java,
            String::class.java,
            String::class.java,
            Map::class.java,
            ByteArray::class.java,
        )
    }

    @Test
    fun allChangedPublicDataTypesKeepLegacyConstructors() {
        assertLegacyConstructors(
            SourceSearchRequest::class.java,
            String::class.java,
            Int::class.javaPrimitiveType!!,
        )
        assertLegacyConstructors(
            SourceResolveRequest::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Float::class.javaObjectType,
            String::class.java,
            String::class.java,
        )
        assertLegacyConstructors(
            SourceHttpResponse::class.java,
            Int::class.javaPrimitiveType!!,
            String::class.java,
            Map::class.java,
            ByteArray::class.java,
        )
    }

    private fun assertLegacyConstructors(type: Class<*>, vararg parameters: Class<*>) {
        assertNotNull(type.getConstructor(*parameters))
        assertNotNull(type.getConstructor(*parameters, Int::class.javaPrimitiveType!!, DefaultConstructorMarker::class.java))
    }
}
