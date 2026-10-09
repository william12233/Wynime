package com.wynime.app.domain.media.selector

import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.source.MediaFetchRequest
import kotlin.test.Test
import kotlin.test.assertTrue

class MediaSelectorRequestedSubjectNamesTest {
    @Test
    fun `fetch request names and equivalent season spellings are selector aliases`() {
        val request = MediaFetchRequest(
            subjectId = "1",
            episodeId = "2",
            subjectNameCN = "大王饒命第三季",
            subjectNames = listOf("大王饒命第三季", "alternate title"),
            episodeSort = EpisodeSort(3),
            episodeName = "第3集",
        )

        val subjectInfo = SubjectInfo.Empty.copy(nameCn = "大王饒命第三季")
            .withFetchRequestSubjectNames(request)

        assertTrue(subjectInfo.allNames.contains("大王饒命3"))
        assertTrue(subjectInfo.allNames.contains("大王饒命第三季"))
        assertTrue(subjectInfo.allNames.contains("alternate title"))
    }
}
