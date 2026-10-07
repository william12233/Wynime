package com.wynime.app.data.models.episode

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import com.wynime.app.data.models.subject.preferredDisplayName as subjectPreferredDisplayName
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.EpisodeType
import com.wynime.datasources.api.PackedDate

@Immutable
@Serializable
data class EpisodeInfo(
    val episodeId: Int,

    val type: EpisodeType?,
    val name: String = "",
    val nameCn: String = "",

    val airDate: PackedDate = PackedDate.Invalid,
    @Deprecated("No longer provided")
    val comment: Int = 0,

    val desc: String = "",

    val sort: EpisodeSort = EpisodeSort(""),

    val ep: EpisodeSort? = null,

    val imageMedium: String? = null,

    val imageLarge: String? = null,

) {
    override fun toString(): String {
        return "EpisodeInfo(episodeId=$episodeId, nameCn='$nameCn', sort=$sort)"
    }

    companion object {
        val Empty = EpisodeInfo(0, null)
    }
}

@Stable
val EpisodeInfo.displayName get() = nameCn.ifBlank { name }

@Stable
val EpisodeInfo.nameOrNameCn get() = name.ifBlank { nameCn }

fun EpisodeInfo.preferredDisplayName(useOriginalTitle: Boolean): String =
    if (useOriginalTitle) nameOrNameCn else displayName

@Stable
fun EpisodeInfo.renderEpisodeEp() = sort.toString()
