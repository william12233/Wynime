package com.wynime.app.data.repository.subject

import com.wynime.datasources.api.topic.UnifiedCollectionType

interface SetSubjectCollectionTypeOrDeleteUseCase {
    suspend operator fun invoke(subjectId: Int, collectionType: UnifiedCollectionType?)
}

class SetSubjectCollectionTypeOrDeleteUseCaseImpl(
    private val subjectRepository: SubjectCollectionRepository,
) : SetSubjectCollectionTypeOrDeleteUseCase {
    override suspend fun invoke(subjectId: Int, collectionType: UnifiedCollectionType?) {
        subjectRepository.setSubjectCollectionTypeOrDelete(subjectId, collectionType)
    }
}
