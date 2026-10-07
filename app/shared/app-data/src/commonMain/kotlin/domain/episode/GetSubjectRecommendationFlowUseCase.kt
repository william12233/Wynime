package com.wynime.app.domain.episode

import com.wynime.app.data.network.SubjectService
import com.wynime.app.data.models.subject.preferredDisplayName as subjectPreferredDisplayName
import com.wynime.app.domain.usecase.UseCase
import com.wynime.utils.platform.Uuid

class SubjectRecommendation(
    val subjectId: Long?,
    val name: String,
    val nameCn: String?,
    val desc1: String,
    val desc2: String,
    val imageUrl: String,
    val uri: String?,
) {
    val uniqueId: String = Uuid.randomString()
}

fun SubjectRecommendation.preferredDisplayName(useOriginalTitle: Boolean): String =
    if (useOriginalTitle) {
        name.ifBlank { nameCn.orEmpty() }
    } else {
        nameCn.takeIf { !it.isNullOrBlank() } ?: name
    }

fun interface GetSubjectRecommendationUseCase : UseCase {
    suspend operator fun invoke(subjectId: Int): List<SubjectRecommendation>
}

class GetSubjectRecommendationUseCaseImpl(private val service: SubjectService) : GetSubjectRecommendationUseCase {
    override suspend fun invoke(subjectId: Int): List<SubjectRecommendation> {
        return service.getSubjectRecommendations(subjectId, 15).map {
            SubjectRecommendation(
                subjectId = it.subjectId,
                name = it.subjectName,
                nameCn = it.subjectNameCn,
                desc1 = it.desc1,
                desc2 = it.desc2,
                imageUrl = it.imageUrl,
                uri = it.uri,
            )
        }
    }
}
