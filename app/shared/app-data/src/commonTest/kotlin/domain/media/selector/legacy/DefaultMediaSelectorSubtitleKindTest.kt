@file:Suppress("DEPRECATION")

package com.wynime.app.domain.media.selector.legacy

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.wynime.app.domain.media.selector.MediaExclusionReason
import com.wynime.app.domain.media.selector.MediaSelectorSubtitlePreferences
import com.wynime.app.domain.media.selector.SubtitleKindPreference
import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.SubtitleKind
import com.wynime.utils.platform.Arch
import com.wynime.utils.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals

@Deprecated(MediaSelectorDeprecationMessage)
class DefaultMediaSelectorSubtitleKindTest : AbstractDefaultMediaSelectorTest() {
    @Test
    fun `AllNormal does not hide`() = runTest {
        setSubtitlePreferences(MediaSelectorSubtitlePreferences.Companion.AllNormal)
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1").also { target = it },
            media(alliance = "字幕组2"),
            media(alliance = "字幕组3"),
            media(alliance = "字幕组4"),
            media(alliance = "字幕组5"),
        )
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `does not select hidden`() = runTest {
        setSubtitlePreference(SubtitleKind.CLOSED, SubtitleKindPreference.HIDE)
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1", subtitleKind = SubtitleKind.CLOSED),
            media(alliance = "字幕组2").also { target = it },
            media(alliance = "字幕组3"),
            media(alliance = "字幕组4"),
            media(alliance = "字幕组5"),
        )
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `low priority ones are selected last`() = runTest {
        setSubtitlePreference(SubtitleKind.CLOSED, SubtitleKindPreference.LOW_PRIORITY)
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1", subtitleKind = SubtitleKind.CLOSED),
            media(alliance = "字幕组2").also { target = it },
            media(alliance = "字幕组3"),
            media(alliance = "字幕组4"),
            media(alliance = "字幕组5"),
        )
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        assertEquals(target, selector.trySelectDefault())
    }

    @Test
    fun `hidden items are not in mediaList`() = runTest {
        setSubtitlePreference(SubtitleKind.CLOSED, SubtitleKindPreference.HIDE)
        addMedia(
            media(alliance = "字幕组1", subtitleKind = SubtitleKind.CLOSED),
            media(alliance = "字幕组2"),
            media(alliance = "字幕组3"),
            media(alliance = "字幕组4"),
            media(alliance = "字幕组5"),
        )
        selector.filteredCandidates.first().run {
            assertEquals(5, size)
            assertEquals(MediaExclusionReason.UnsupportedByPlatformPlayer, get(4).exclusionReason)
        }
        assertEquals(4, selector.filteredCandidatesMedia.first().size)
    }

    @Test
    fun `hidden items are not in filteredCandidates`() = runTest {
        setSubtitlePreference(SubtitleKind.CLOSED, SubtitleKindPreference.HIDE)
        addMedia(
            media(alliance = "字幕组1", subtitleKind = SubtitleKind.CLOSED),
            media(alliance = "字幕组2"),
            media(alliance = "字幕组3"),
            media(alliance = "字幕组4"),
            media(alliance = "字幕组5"),
        )
        assertEquals(4, selector.preferredCandidatesMedia.first().size)
    }
}

@Deprecated(MediaSelectorDeprecationMessage)
sealed class DefaultMediaSelectorSubtitleKindPlatformTest(
    platform: Platform
) : AbstractDefaultMediaSelectorTest() {

    class Windows : DefaultMediaSelectorSubtitleKindPlatformTest(Platform.Windows(Arch.X86_64))
    class Android : DefaultMediaSelectorSubtitleKindPlatformTest(Platform.Android(Arch.ARMV8A))

    init {
        setSubtitlePreferences(MediaSelectorSubtitlePreferences.Companion.forPlatform(platform))
    }

    @Test
    fun `does not select EXTERNAL_DISCOVER`() = runTest {
        val target: DefaultMedia
        addMedia(
            media(alliance = "字幕组1", subtitleKind = SubtitleKind.EXTERNAL_DISCOVER),
            media(alliance = "字幕组2").also { target = it },
            media(alliance = "字幕组3"),
            media(alliance = "字幕组4"),
            media(alliance = "字幕组5"),
        )
        savedDefaultPreference.value = DEFAULT_PREFERENCE
        assertEquals(target, selector.trySelectDefault())
    }
}
