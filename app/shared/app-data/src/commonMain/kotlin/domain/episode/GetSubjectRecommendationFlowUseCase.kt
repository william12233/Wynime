package com.wynime.app.domain.episode

import com.wynime.app.data.network.SubjectService
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.domain.usecase.UseCase
import com.wynime.models.SubjectRecommendationDto
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

    suspend operator fun invoke(subjectInfo: SubjectInfo): List<SubjectRecommendation> = invoke(subjectInfo.subjectId)
}

class GetSubjectRecommendationUseCaseImpl(private val service: SubjectService) : GetSubjectRecommendationUseCase {
    override suspend fun invoke(subjectId: Int): List<SubjectRecommendation> =
        service.getSubjectRecommendations(subjectId, 15).map { it.toSubjectRecommendation() }

    override suspend fun invoke(subjectInfo: SubjectInfo): List<SubjectRecommendation> {
        val relatedTags = subjectInfo.tags
            .asSequence()
            .map { it.name.trim() }
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
        return service.getSubjectRecommendations(subjectInfo.subjectId, relatedTags, 15)
            .map { it.toSubjectRecommendation() }
    }
}

private fun SubjectRecommendationDto.toSubjectRecommendation() = SubjectRecommendation(
    subjectId = subjectId,
    name = subjectName,
    nameCn = subjectNameCn,
    desc1 = desc1,
    desc2 = desc2,
    imageUrl = imageUrl,
    uri = uri,
)
