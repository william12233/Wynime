package com.wynime.app.ui.mediafetch.request

import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.utils.platform.annotations.TestOnly

data class EditingMediaFetchRequest(
    val subjectId: String,
    val episodeId: String,
    val primaryName: String,
    val complementaryNames: List<String>,
    val episodeSort: String,
    val episodeName: String,
    val episodeEp: String,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        @Stable
        val Saver = Saver<EditingMediaFetchRequest, List<Any>>(
            save = {
                with(it) {
                    listOf(
                        subjectId,
                        episodeId,
                        primaryName,
                        complementaryNames,
                        episodeSort,
                        episodeName,
                        episodeEp,
                    )
                }
            },
            restore = {
                EditingMediaFetchRequest(
                    subjectId = it[0] as String,
                    episodeId = it[1] as String,
                    primaryName = it[2] as String,
                    complementaryNames = it[3] as List<String>,
                    episodeSort = it[4] as String,
                    episodeName = it[5] as String,
                    episodeEp = it[6] as String,
                )
            },
        )
    }
}

fun MediaFetchRequest.toEditingMediaFetchRequest(): EditingMediaFetchRequest {
    return EditingMediaFetchRequest(
        subjectId = subjectId,
        episodeId = episodeId,
        primaryName = subjectNames.getOrNull(0) ?: "",
        complementaryNames = subjectNames.drop(1),
        episodeSort = episodeSort.toString(),
        episodeName = episodeName,
        episodeEp = episodeEp?.toString().orEmpty(),
    )
}

fun EditingMediaFetchRequest.toMediaFetchRequestOrNull(
    episodes: List<MediaFetchRequest.Episode> = emptyList(),
): MediaFetchRequest? {
    return MediaFetchRequest(
        subjectId = subjectId.toIntOrNull()?.toString() ?: return null,
        episodeId = episodeId.toIntOrNull()?.toString() ?: return null,
        subjectNameCN = primaryName,
        subjectNames = listOf(primaryName) + complementaryNames,
        episodeSort = EpisodeSort(episodeSort),
        episodeName = episodeName,
        episodeEp = EpisodeSort(episodeEp),
        episodes = episodes,
    )
}

@TestOnly
val TestEditingMediaFetchRequest
    get() = EditingMediaFetchRequest(
        subjectId = "12345",
        episodeId = "67890",
        primaryName = "关于我转生变成史莱姆这档事 第三季",
        complementaryNames = listOf(
            "転生したらスライムだった件 第3期",
            "关于我转生变成史莱姆这档事 第三季",
            "Tensei Shitara Slime Datta Ken Season 3",
        ),
        episodeSort = "49",
        episodeName = "恶魔与阴谋",
        episodeEp = "01",
    )

@TestOnly
val TestMediaFetchRequest
    get() = MediaFetchRequest(
        subjectId = "12345",
        episodeId = "67890",
        subjectNameCN = "关于我转生变成史莱姆这档事 第三季",
        subjectNames = listOf(
            "転生したらスライムだった件 第3期",
            "关于我转生变成史莱姆这档事 第三季",
            "Tensei Shitara Slime Datta Ken Season 3",
        ),
        episodeSort = EpisodeSort("49"),
        episodeName = "恶魔与阴谋",
        episodeEp = EpisodeSort("01"),
    )
