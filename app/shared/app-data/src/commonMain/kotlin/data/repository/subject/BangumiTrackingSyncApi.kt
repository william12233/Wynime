package com.wynime.app.data.repository.subject

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import com.wynime.app.data.network.BangumiApiProvider
import com.wynime.app.data.repository.RepositoryException
import com.wynime.datasources.api.topic.UnifiedCollectionType
import com.wynime.datasources.bangumi.models.BangumiSubjectCollectionType
import com.wynime.datasources.bangumi.models.BangumiSubjectType
import com.wynime.datasources.bangumi.models.BangumiUserSubjectCollectionModifyPayload
import kotlin.coroutines.cancellation.CancellationException

interface BangumiTrackingSyncApi {
    suspend fun currentUser(): BangumiTrackingAccount?

    suspend fun animeCollections(
        username: String,
        limit: Int,
        offset: Int,
    ): BangumiTrackingRemotePage

    suspend fun collection(username: String, subjectId: Int): BangumiTrackingRemoteSnapshot?

    suspend fun upsertCollectionType(subjectId: Int, type: UnifiedCollectionType)
}

data class BangumiTrackingRemotePage(
    val collections: List<BangumiTrackingRemoteSnapshot>,
    val total: Int?,
)

class BangumiTrackingSyncApiImpl(
    private val provider: BangumiApiProvider,
) : BangumiTrackingSyncApi {
    override suspend fun currentUser(): BangumiTrackingAccount? = request {
        provider.currentUser()?.let { BangumiTrackingAccount(it.id, it.username) }
    }

    override suspend fun animeCollections(
        username: String,
        limit: Int,
        offset: Int,
    ): BangumiTrackingRemotePage = request {
        provider.request {
            getUserCollectionsByUsername(
                username = username,
                subjectType = BangumiSubjectType.Anime,
                limit = limit,
                offset = offset,
            )
        }.let { page ->
            BangumiTrackingRemotePage(
                collections = page.data.orEmpty().map { collection ->
                    BangumiTrackingRemoteSnapshot(
                        subjectId = collection.subjectId,
                        type = collection.type.toUnifiedCollectionType(),
                        updatedAt = collection.updatedAt.toEpochMilliseconds(),
                    )
                },
                total = page.total,
            )
        }
    }

    override suspend fun collection(
        username: String,
        subjectId: Int,
    ): BangumiTrackingRemoteSnapshot? = request {
        try {
            provider.request {
                getUserCollection(username = username, subjectId = subjectId)
            }.let { collection ->
                BangumiTrackingRemoteSnapshot(
                    subjectId = collection.subjectId,
                    type = collection.type.toUnifiedCollectionType(),
                    updatedAt = collection.updatedAt.toEpochMilliseconds(),
                )
            }
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) null else throw e
        }
    }

    override suspend fun upsertCollectionType(subjectId: Int, type: UnifiedCollectionType) {
        require(type != UnifiedCollectionType.NOT_COLLECTED) {
            "Bangumi has no public remote delete operation"
        }
        request {
            provider.request {
                postUserCollection(
                    subjectId = subjectId,
                    bangumiUserSubjectCollectionModifyPayload = BangumiUserSubjectCollectionModifyPayload(
                        type = type.toBangumiCollectionType(),
                    ),
                )
            }
        }
    }

    private suspend fun <T> request(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw RepositoryException.wrapOrThrowCancellation(e)
    }
}

private fun BangumiSubjectCollectionType.toUnifiedCollectionType(): UnifiedCollectionType = when (this) {
    BangumiSubjectCollectionType.Wish -> UnifiedCollectionType.WISH
    BangumiSubjectCollectionType.Done -> UnifiedCollectionType.DONE
    BangumiSubjectCollectionType.Doing -> UnifiedCollectionType.DOING
    BangumiSubjectCollectionType.OnHold -> UnifiedCollectionType.ON_HOLD
    BangumiSubjectCollectionType.Dropped -> UnifiedCollectionType.DROPPED
}

private fun UnifiedCollectionType.toBangumiCollectionType(): BangumiSubjectCollectionType = when (this) {
    UnifiedCollectionType.WISH -> BangumiSubjectCollectionType.Wish
    UnifiedCollectionType.DONE -> BangumiSubjectCollectionType.Done
    UnifiedCollectionType.DOING -> BangumiSubjectCollectionType.Doing
    UnifiedCollectionType.ON_HOLD -> BangumiSubjectCollectionType.OnHold
    UnifiedCollectionType.DROPPED -> BangumiSubjectCollectionType.Dropped
    UnifiedCollectionType.NOT_COLLECTED -> error("NOT_COLLECTED has no Bangumi mutation")
}
