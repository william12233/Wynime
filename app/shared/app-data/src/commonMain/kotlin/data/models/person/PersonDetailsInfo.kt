package com.wynime.app.data.models.person

import androidx.compose.runtime.Immutable
import com.wynime.app.data.models.subject.CharacterInfo
import com.wynime.app.data.models.subject.CharacterRole
import com.wynime.app.data.models.subject.PersonInfo
import com.wynime.app.data.models.subject.PersonPosition

@Immutable
data class PersonDetailsInfo(
    val person: PersonInfo,

    val career: List<String>,

    val infobox: List<InfoboxRowInfo>,
    val collects: Int,
    val commentCount: Int,

    val workCount: Int,

    val castCount: Int,
)

@Immutable
data class CharacterDetailsInfo(

    val character: CharacterInfo,

    val role: Int,
    val summary: String,

    val infobox: List<InfoboxRowInfo>,
    val collects: Int,
    val commentCount: Int,

    val subjectCount: Int,
)

@Immutable
data class InfoboxRowInfo(
    val key: String,
    val value: String,
)

@Immutable
data class PersonSubjectSummary(
    val subjectId: Int,
    val name: String,
    val nameCn: String,
    val imageLarge: String,
) {
    val displayName get() = nameCn.takeIf { it.isNotBlank() } ?: name
}

@Immutable
data class PersonWorkInfo(
    val subject: PersonSubjectSummary,
    val positions: List<PersonPosition>,
)

@Immutable
data class PersonCastInfo(
    val subject: PersonSubjectSummary,
    val character: CharacterInfo,
)

@Immutable
data class CharacterSubjectInfo(
    val subject: PersonSubjectSummary,
    val role: CharacterRole,
    val actors: List<PersonInfo>,
)
