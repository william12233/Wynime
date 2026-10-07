package com.wynime.app.data.network

import com.wynime.cloud.models.CollectionRemoval
import com.wynime.app.data.repository.RepositoryAuthorizationException
import com.wynime.app.data.repository.RepositoryRequestError
import com.wynime.app.data.repository.user.TokenRepository

class CollectionRemovalService(
    private val cloudClient: WynimeCloudClient,
    private val tokenRepository: TokenRepository,
) {
    suspend fun start(subjectId: Int): String = withSession { token ->
        val operation = cloudClient.createCollectionRemoval(token, subjectId)
        check(operation.subjectId == subjectId)
        val expectedUrl = "https://bgm.tv/subject/$subjectId"
        check(operation.webUrl == expectedUrl)
        expectedUrl
    }

    suspend fun confirm(subjectId: Int) = withSession { token ->
        val pending = cloudClient.createCollectionRemoval(token, subjectId)
        check(pending.subjectId == subjectId)
        val operation = cloudClient.confirmCollectionRemoval(token, subjectId)
        check(operation.subjectId == subjectId)
        if (operation.status != CollectionRemoval.Status.confirmed) {
            throw RepositoryRequestError("待 Bangumi 網頁取消收藏並確認；此操作仍保留在同步佇列")
        }
    }

    private suspend fun <T> withSession(block: suspend (String) -> T): T {
        val session = tokenRepository.getTokenSaveSnapshot()
        val sessionToken = session.refreshToken?.takeIf(String::isNotBlank)
            ?: throw RepositoryAuthorizationException("請先登入 Bangumi")
        val result = block(sessionToken)
        if (tokenRepository.getTokenSaveSnapshot() != session) {
            throw RepositoryAuthorizationException("帳號已變更，請重新確認取消收藏")
        }
        return result
    }
}
