package com.wynime.app.data.repository.person

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import com.wynime.app.data.models.person.CharacterDetailsInfo
import com.wynime.app.data.models.person.CharacterSubjectInfo
import com.wynime.app.data.models.person.InfoboxRowInfo
import com.wynime.app.data.models.person.PersonCastInfo
import com.wynime.app.data.models.person.PersonDetailsInfo
import com.wynime.app.data.models.person.PersonSubjectSummary
import com.wynime.app.data.models.person.PersonWorkInfo
import com.wynime.app.data.models.subject.CharacterInfo
import com.wynime.app.data.models.subject.CharacterRole
import com.wynime.app.data.models.subject.PersonCareer
import com.wynime.app.data.models.subject.PersonInfo
import com.wynime.app.data.models.subject.PersonPosition
import com.wynime.app.data.models.subject.PersonType
import com.wynime.app.data.network.BangumiApiProvider
import com.wynime.app.data.repository.Repository
import com.wynime.app.data.repository.RepositoryException
import com.wynime.datasources.bangumi.models.BangumiCharacterPerson
import com.wynime.datasources.bangumi.models.BangumiCharacterDetail
import com.wynime.datasources.bangumi.models.BangumiPerson
import com.wynime.datasources.bangumi.models.BangumiPersonCareer
import com.wynime.datasources.bangumi.models.BangumiPersonCharacter
import com.wynime.datasources.bangumi.models.BangumiPersonDetail
import com.wynime.datasources.bangumi.models.BangumiV0RelatedSubject

class PersonDetailsRepository(
    private val bangumiApi: BangumiApiProvider,
    defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : Repository(defaultDispatcher) {

    fun personDetailsFlow(personId: Int): Flow<PersonDetailsInfo> = flow {
        val details = try {
            withContext(defaultDispatcher) {
                val person = bangumiApi.request { getPersonById(personId) }
                val works = bangumiApi.request { getRelatedSubjectsByPersonId(personId) }
                val casts = bangumiApi.request { getRelatedCharactersByPersonId(personId) }
                PersonDetailsInfo(
                    person = person.toPersonInfo(),
                    career = person.career.map(BangumiPersonCareer::toString),
                    infobox = person.infobox.toRows(),
                    collects = person.stat.collects,
                    commentCount = person.stat.comments,
                    workCount = works.size,
                    castCount = casts.size,
                )
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
        emit(details)
    }

    fun characterDetailsFlow(characterId: Int): Flow<CharacterDetailsInfo> = flow {
        val details = try {
            withContext(defaultDispatcher) {
                val character = bangumiApi.request { getCharacterById(characterId) }
                val actors = bangumiApi.request { getRelatedPersonsByCharacterId(characterId) }
                val subjects = bangumiApi.request { getRelatedSubjectsByCharacterId(characterId) }
                CharacterDetailsInfo(
                    character = character.toCharacterInfo(actors.map(BangumiCharacterPerson::toPersonInfo)),
                    role = character.type.value,
                    summary = character.summary,
                    infobox = character.infobox.toRows(),
                    collects = character.stat.collects,
                    commentCount = character.stat.comments,
                    subjectCount = subjects.size,
                )
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
        emit(details)
    }

    fun personWorksPager(personId: Int): Flow<PagingData<PersonWorkInfo>> = offsetPager { offset, limit ->
        val works = bangumiApi.request { getRelatedSubjectsByPersonId(personId) }
        Paged(
            total = works.size,
            items = works.drop(offset).take(limit).map { work ->
                PersonWorkInfo(
                    subject = work.toSummary(),
                    positions = work.staff.toPositions(),
                )
            },
        )
    }

    fun personCastsPager(personId: Int): Flow<PagingData<PersonCastInfo>> = offsetPager { offset, limit ->
        val casts = bangumiApi.request { getRelatedCharactersByPersonId(personId) }
        Paged(
            total = casts.size,
            items = casts.drop(offset).take(limit).map { cast ->
                PersonCastInfo(
                    subject = PersonSubjectSummary(
                        subjectId = cast.subjectId,
                        name = cast.subjectName,
                        nameCn = cast.subjectNameCn,
                        imageLarge = cast.images?.large.orEmpty(),
                    ),
                    character = cast.toCharacterInfo(),
                )
            },
        )
    }

    fun characterSubjectsPager(characterId: Int): Flow<PagingData<CharacterSubjectInfo>> =
        offsetPager { offset, limit ->
            val subjects = bangumiApi.request { getRelatedSubjectsByCharacterId(characterId) }
            val actors = bangumiApi.request { getRelatedPersonsByCharacterId(characterId) }
                .map(BangumiCharacterPerson::toPersonInfo)
            Paged(
                total = subjects.size,
                items = subjects.drop(offset).take(limit).map { subject ->
                    CharacterSubjectInfo(
                        subject = subject.toSummary(),
                        role = CharacterRole(0),
                        actors = actors,
                    )
                },
            )
        }

    private class Paged<T>(val total: Int, val items: List<T>)

    private fun <T : Any> offsetPager(
        fetch: suspend (offset: Int, limit: Int) -> Paged<T>,
    ): Flow<PagingData<T>> = Pager(
        config = defaultPagingConfig,
        initialKey = 0,
        pagingSourceFactory = { OffsetPagingSource(fetch) },
    ).flow

    private inner class OffsetPagingSource<T : Any>(
        private val fetch: suspend (offset: Int, limit: Int) -> Paged<T>,
    ) : PagingSource<Int, T>() {
        override fun getRefreshKey(state: PagingState<Int, T>): Int? = state.anchorPosition

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> = withContext(defaultDispatcher) {
            val offset = params.key ?: 0

            val limit = params.loadSize.coerceIn(1, 100)
            try {
                val page = fetch(offset, limit)
                val nextOffset = offset + page.items.size
                LoadResult.Page(
                    data = page.items,
                    prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                    nextKey = if (page.items.isNotEmpty() && nextOffset < page.total) nextOffset else null,
                )
            } catch (e: Exception) {
                LoadResult.Error(RepositoryException.wrapOrThrowCancellation(e))
            }
        }
    }
}

private fun BangumiPersonDetail.toPersonInfo() = PersonInfo(
    id = id,
    name = name,
    type = PersonType.fromId(type.value),
    careers = career.map(BangumiPersonCareer::toPersonCareer),
    imageLarge = images?.large.orEmpty(),
    imageMedium = images?.medium.orEmpty(),
    summary = summary,
    locked = locked,
    nameCn = "",
)

private fun BangumiPerson.toPersonInfo() = PersonInfo(
    id = id,
    name = name,
    type = PersonType.fromId(type.value),
    careers = career.map(BangumiPersonCareer::toPersonCareer),
    imageLarge = images?.large.orEmpty(),
    imageMedium = images?.medium.orEmpty(),
    summary = shortSummary,
    locked = locked,
    nameCn = "",
)

private fun BangumiCharacterPerson.toPersonInfo() = PersonInfo(
    id = id,
    name = name,
    type = PersonType.Individual,
    careers = emptyList(),
    imageLarge = images?.large.orEmpty(),
    imageMedium = images?.medium.orEmpty(),
    summary = "",
    locked = null,
    nameCn = "",
)

private fun BangumiCharacterDetail.toCharacterInfo(actors: List<PersonInfo>) = CharacterInfo(
    id = id,
    name = name,
    nameCn = "",
    actors = actors,
    imageMedium = images?.medium.orEmpty(),
    imageLarge = images?.large.orEmpty(),
)

private fun BangumiPersonCharacter.toCharacterInfo() = CharacterInfo(
    id = id,
    name = name,
    nameCn = "",
    actors = emptyList(),
    imageMedium = images?.medium.orEmpty(),
    imageLarge = images?.large.orEmpty(),
)

private fun BangumiV0RelatedSubject.toSummary() = PersonSubjectSummary(
    subjectId = id,
    name = name.orEmpty(),
    nameCn = nameCn,
    imageLarge = image.orEmpty(),
)

private fun String.toPositions(): List<PersonPosition> {
    val positions = split('、', ',', '，', '/', '|')
        .map { PersonPosition.findByName(it.trim()) }
        .filter { it != PersonPosition.Invalid }
    return positions.ifEmpty { listOf(PersonPosition.Invalid) }
}

private fun List<String>?.toRows(): List<InfoboxRowInfo> = orEmpty().mapIndexedNotNull { index, item ->
    val separator = item.indexOfAny(charArrayOf(':', '：'))
    val key = if (separator > 0) item.substring(0, separator).trim() else "info${index + 1}"
    val value = if (separator > 0) item.substring(separator + 1).trim() else item.trim()
    if (key in HIDDEN_INFOBOX_KEYS || value.isBlank()) null else InfoboxRowInfo(key, value)
}

private fun BangumiPersonCareer.toPersonCareer() = when (this) {
    BangumiPersonCareer.PRODUCER -> PersonCareer.PRODUCER
    BangumiPersonCareer.MANGAKA -> PersonCareer.MANGAKA
    BangumiPersonCareer.ARTIST -> PersonCareer.ARTIST
    BangumiPersonCareer.SEIYU -> PersonCareer.SEIYU
    BangumiPersonCareer.WRITER -> PersonCareer.WRITER
    BangumiPersonCareer.ILLUSTRATOR -> PersonCareer.ILLUSTRATOR
    BangumiPersonCareer.ACTOR -> PersonCareer.ACTOR
}

private val HIDDEN_INFOBOX_KEYS = setOf("简体中文名", "中文名", "名前", "name")
