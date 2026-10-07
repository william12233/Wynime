package com.wynime.utils.ktor

import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.decode
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.streams.asInput
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

actual fun getPlatformKtorEngine(): HttpClientEngineFactory<*> = OkHttp

suspend inline fun HttpResponse.bodyAsDocument(): Document = body()

internal actual fun getXmlConverter(): ContentConverter = XmlConverter

private object XmlConverter : ContentConverter {
    override suspend fun serialize(
        contentType: ContentType,
        charset: io.ktor.utils.io.charsets.Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent? = null

    override suspend fun deserialize(
        charset: io.ktor.utils.io.charsets.Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ): Any? {
        if (typeInfo.type.qualifiedName != Document::class.qualifiedName) return null
        content.awaitContent()
        val decoder = Charsets.UTF_8.newDecoder()
        val string = decoder.decode(content.toInputStream().asInput())
        return Jsoup.parse(string, charset.name())
    }
}
