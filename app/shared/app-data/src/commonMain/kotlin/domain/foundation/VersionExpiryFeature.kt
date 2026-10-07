package com.wynime.app.domain.foundation

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpStatusCode

val VersionExpiryFeature = ScopedHttpClientFeatureKey<Boolean>("VersionExpiry")

data object VersionExpiryFeatureHandler : ScopedHttpClientFeatureHandler<Boolean>(VersionExpiryFeature) {
    override fun applyToClient(client: HttpClient, value: Boolean) {
        if (!value) return
        client.plugin(HttpSend).intercept { request ->
            fun handleResp(resp: io.ktor.client.statement.HttpResponse) {
                val latest = resp.headers["X-Latest-Version"]
                GlobalHttpEventBus.onVersionExpired(latest)
            }

            val call = try {
                execute(request)
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.UpgradeRequired) {
                    handleResp(e.response)
                }
                throw e
            }

            if (call.response.status == HttpStatusCode.UpgradeRequired) {
                handleResp(call.response)
            }

            call
        }
    }
}

