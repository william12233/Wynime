package com.wynime.app.ui.mediafetch

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.wynime.app.domain.media.selector.MaybeExcludedMedia
import com.wynime.app.domain.media.selector.MediaExclusionReason
import com.wynime.app.domain.media.selector.UnsafeOriginalMediaAccess
import com.wynime.datasources.api.Media

@Immutable
class MediaGroup(
    val groupId: MediaGroupId,
    val list: List<MaybeExcludedMedia>,
) {
    @Stable
    val first: MaybeExcludedMedia = list.first()

    @Stable
    val isExcluded = list.all { it is MaybeExcludedMedia.Excluded }

    @Stable
    val exclusionReason: MediaExclusionReason? = list.firstOrNull { it.exclusionReason != null }?.exclusionReason

}

class MediaGroupBuilder(
    val id: String,
) {
    private val _list: ArrayList<MaybeExcludedMedia> = ArrayList(4)

    fun add(media: MaybeExcludedMedia) {
        _list.add(media)
    }

    fun build(): MediaGroup = MediaGroup(id, _list)
}

internal typealias MediaGroupId = String

object MediaGrouper {
    fun getGroupId(media: Media): String {

        return "media-group-${media.mediaId}"
    }

    fun getItemIdWithinGroup(media: Media): String {
        val alliance = media.properties.alliance
        if (alliance.isNotEmpty()) return alliance

        val title = media.originalTitle
        if (title.startsWith('[')) {
            val index = title.indexOf(']')
            if (index != -1) {
                return title.substring(1, index)
            }
        }

        return title
    }

    @OptIn(UnsafeOriginalMediaAccess::class)
    fun buildGroups(list: List<MaybeExcludedMedia>): List<MediaGroup> {
        val groups = LinkedHashMap<String, MediaGroupBuilder>()
        for (media in list) {
            val groupId = getGroupId(media.original)
            groups.getOrPut(groupId) { MediaGroupBuilder(groupId) }
                .add(media)
        }
        return groups.values.map { it.build() }
    }
}
