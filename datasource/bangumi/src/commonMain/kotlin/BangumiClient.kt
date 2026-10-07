package com.wynime.datasources.bangumi

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import com.wynime.datasources.api.source.ConnectionStatus
import com.wynime.utils.ktor.ScopedHttpClient

interface BangumiClient {

    suspend fun testConnectionMaster(): ConnectionStatus

    suspend fun testConnectionNext(): ConnectionStatus
}

private const val BANGUMI_API_HOST = "https://api.bgm.tv"
private const val BANGUMI_NEXT_API_HOST = "https://next.bgm.tv"

class BangumiClientImpl(

    private val client: ScopedHttpClient,
) : BangumiClient {

    override suspend fun testConnectionMaster(): ConnectionStatus {
        return testConnection(BANGUMI_API_HOST)
    }

    override suspend fun testConnectionNext(): ConnectionStatus {
        return testConnection(BANGUMI_NEXT_API_HOST)
    }

    private suspend fun testConnection(host: String): ConnectionStatus {
        return client.use {
            get(host).run {
                if (status.isSuccess() || status == HttpStatusCode.NotFound)
                    ConnectionStatus.SUCCESS
                else ConnectionStatus.FAILED
            }
        }
    }
}
