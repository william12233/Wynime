package com.wynime.app.data.repository.subject

import com.wynime.datasources.api.topic.UnifiedCollectionType

interface SetSubjectCollectionTypeOrDeleteUseCase {
    suspend operator fun invoke(subjectId: Int, collectionType: UnifiedCollectionType?)
}

class SetSubjectCollectionTypeOrDeleteUseCaseImpl(
    private val subjectRepository: SubjectCollectionRepository,
    private val prepareRemoval: suspend (Int) -> Unit,
) : SetSubjectCollectionTypeOrDeleteUseCase {
    override suspend fun invoke(subjectId: Int, collectionType: UnifiedCollectionType?) {
        if (collectionType == null || collectionType == UnifiedCollectionType.NOT_COLLECTED) {
            prepareRemoval(subjectId)
        }
        subjectRepository.setSubjectCollectionTypeOrDelete(subjectId, collectionType)
    }
}
