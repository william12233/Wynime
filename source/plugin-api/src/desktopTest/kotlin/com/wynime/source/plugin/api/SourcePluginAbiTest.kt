package com.wynime.source.plugin.api

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
