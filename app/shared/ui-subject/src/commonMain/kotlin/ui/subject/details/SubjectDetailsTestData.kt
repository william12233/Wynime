package com.wynime.app.ui.subject.details

import com.wynime.app.data.models.subject.CharacterInfo
import com.wynime.app.data.models.subject.CharacterRole
import com.wynime.app.data.models.subject.Images
import com.wynime.app.data.models.subject.PersonCareer
import com.wynime.app.data.models.subject.PersonInfo
import com.wynime.app.data.models.subject.PersonPosition
import com.wynime.app.data.models.subject.PersonType
import com.wynime.app.data.models.subject.RatingCounts
import com.wynime.app.data.models.subject.RatingInfo
import com.wynime.app.data.models.subject.RelatedCharacterInfo
import com.wynime.app.data.models.subject.RelatedPersonInfo
import com.wynime.app.data.models.subject.RelatedSubjectInfo
import com.wynime.app.data.models.subject.SubjectCollectionStats
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.SubjectRelation
import com.wynime.app.data.models.subject.Tag
import com.wynime.app.data.models.subject.TestCoverImage
import com.wynime.datasources.api.PackedDate
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.random.Random

@TestOnly
internal fun testPersonInfo(
    name: String,
    type: PersonType = PersonType.Individual,
    careers: List<PersonCareer> = emptyList(),
    summary: String = """一个测试人物""",
    locked: Boolean = false,
    images: Images? = null,
) = PersonInfo(
    id = Random.nextInt(),
    name = name,
    type = type,
    careers = careers,
    imageLarge = images?.large ?: TestCoverImage,
    imageMedium = images?.medium ?: TestCoverImage,
    summary = summary,
    locked = locked,
)

@TestOnly
internal fun testRelatedPersonInfo(
    index: Int,
    name: String,
    position: PersonPosition,
    type: PersonType = PersonType.Individual,
    careers: List<PersonCareer> = emptyList(),
    shortSummary: String = """一个测试人物""",
    locked: Boolean = false,
    images: Images? = null,
) = RelatedPersonInfo(
    index = 0,
    personInfo = testPersonInfo(name, type, careers, shortSummary, locked, images),
    position = position,
)

@TestOnly
internal val TestSubjectStaffInfo
    get() = listOf(
        testRelatedPersonInfo(
            0,
            "CloverWorks",
            position = PersonPosition.AnimationWork,
            type = PersonType.Corporation,
        ),
        testRelatedPersonInfo(1, "はまじあき", position = PersonPosition.OriginalWork),
        testRelatedPersonInfo(2, "斎藤圭一郎", position = PersonPosition.Director),
        testRelatedPersonInfo(3, "吉田恵里香", position = PersonPosition.CharacterDesign),
        testRelatedPersonInfo(4, "菊谷知樹", position = PersonPosition.Music),
        testRelatedPersonInfo(5, "けろりら", position = PersonPosition.CharacterDesign),
    )

@TestOnly
internal fun testRelatedCharacterInfo(
    index: Int,
    name: String,
    role: CharacterRole = CharacterRole.MAIN,
    id: Int = 0,
    nameCn: String = name,
    actors: List<PersonInfo> = emptyList(),
): RelatedCharacterInfo =
    RelatedCharacterInfo(
        index,
        CharacterInfo(id, name, nameCn, actors, imageMedium = "", imageLarge = ""),
        role,
    )

@TestOnly
internal fun testRelatedCharacterInfo(
    index: Int,
    name: String,
    actorName: String,
    role: CharacterRole = CharacterRole.MAIN,
    id: Int = 0,
    nameCn: String = name,
): RelatedCharacterInfo = RelatedCharacterInfo(
    index,
    CharacterInfo(
        id, name, nameCn,
        listOf(testPersonInfo(actorName, careers = listOf(PersonCareer.SEIYU))), imageMedium = "", imageLarge = "",
    ),
    role,
)

@TestOnly
internal val TestSubjectCharacterList
    get() = listOf(
        testRelatedCharacterInfo(0, "後藤ひとり", "青山吉能"),
        testRelatedCharacterInfo(1, "伊地知虹夏", "鈴代紗弓"),
        testRelatedCharacterInfo(2, "山田リョウ山田リョウ山田リョウ", "水野朔"),
        testRelatedCharacterInfo(3, "喜多郁代", "長谷川育美"),
        testRelatedCharacterInfo(4, "後藤直樹", "間島淳司"),
        testRelatedCharacterInfo(5, "後藤美智代", "末柄里恵"),
    )

@TestOnly
internal fun testRelatedSubjectInfo(
    nameCn: String,
    relation: SubjectRelation?,
    id: Int = Random.nextInt(),
    name: String? = null,
    image: String? = null,
) = RelatedSubjectInfo(id, relation, name, nameCn, image)

@TestOnly
internal val TestRelatedSubjects
    get() = listOf(
        testRelatedSubjectInfo("孤独摇滚 第二季", SubjectRelation.SEQUEL),
        testRelatedSubjectInfo("孤独摇滚 第零季", SubjectRelation.PREQUEL),
        testRelatedSubjectInfo("孤独摇滚 外传", SubjectRelation.DERIVED),
        testRelatedSubjectInfo("孤独摇滚 OAD", SubjectRelation.SPECIAL),
    )
