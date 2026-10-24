package com.wynime.app.data.repository.subject

import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.network.BatchSubjectDetails
import com.wynime.app.data.network.LightSubjectRelations
import com.wynime.datasources.api.PackedDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SubjectSearchRepositoryTest {
    @Test
    fun `release date sort puts newest valid dates first and invalid dates last`() {
        val subjects = listOf(
            subject(30, PackedDate.Invalid),
            subject(20, PackedDate(2024, 1, 1)),
            subject(10, PackedDate(2025, 1, 1)),
            subject(11, PackedDate(2025, 1, 1)),
        )

        assertEquals(
            listOf(10, 11, 20, 30),
            sortSubjectsByReleaseDate(subjects).map { it.subjectInfo.subjectId },
        )
    }

    private fun subject(subjectId: Int, airDate: PackedDate) = BatchSubjectDetails(
        subjectInfo = SubjectInfo.Empty.copy(
            subjectId = subjectId,
            airDate = airDate,
        ),
        mainEpisodeCount = 0,
        lightSubjectRelations = LightSubjectRelations(
            lightRelatedPersonInfoList = emptyList(),
            lightRelatedCharacterInfoList = emptyList(),
        ),
    )
}
