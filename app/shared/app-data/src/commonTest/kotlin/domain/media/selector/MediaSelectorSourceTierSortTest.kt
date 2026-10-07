@file:OptIn(UnsafeOriginalMediaAccess::class)

package com.wynime.app.domain.media.selector

import com.wynime.app.domain.media.selector.testFramework.MediaSelectorTestSuite
import com.wynime.app.domain.media.selector.testFramework.assertMedias
import com.wynime.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import com.wynime.app.domain.media.selector.testFramework.setChannelTiers
import com.wynime.app.domain.media.selector.testFramework.setSourceTiers
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceKind.WEB
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.test.DisabledOnNative
import kotlin.test.Test

@DisabledOnNative
class MediaSelectorSourceTierSortTest {
    @Test
    fun `tier - basic sorting`() = runSimpleMediaSelectorTestSuite {
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "2", kind = WEB),
            media(sourceId = "1", kind = WEB),
            media(sourceId = "3", kind = WEB),
        )
        setSourceTiers(
            "1" to 0u,
            "2" to 1u,
            "3" to 3u,
        )

        assertMedias {
            next().assert(sourceId = "1")
            next().assert(sourceId = "2")
            next().assert(sourceId = "3")
            assertNoMoreElements()
        }
    }

    @Test
    fun `tier - fallback`() = runSimpleMediaSelectorTestSuite {
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "t0", kind = WEB),
            media(sourceId = "t1", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t3", kind = WEB),
            media(sourceId = "untiered", kind = WEB),
        )
        mediaApi.shuffle()
        setSourceTiers(
            "t0" to 0u,
            "t1" to 1u,
            "t2" to 2u,
            "t3" to 3u,
        )

        assertMedias {
            next().assert(sourceId = "t0")
            next().assert(sourceId = "t1")
            next().assert(sourceId = "untiered")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t3")
            assertNoMoreElements()
        }
    }

    private fun MediaSelectorTestSuite.initSubject() {
        initSubject("test")
    }

    @Test
    fun `tier - multiple same tier`() = runSimpleMediaSelectorTestSuite {
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "t0", kind = WEB),
            media(sourceId = "t1", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t3", kind = WEB),
        )
        mediaApi.shuffle()
        setSourceTiers(
            "t0" to 0u,
            "t1" to 1u,
            "t2" to 2u,
            "t3" to 3u,
        )

        assertMedias {
            next().assert(sourceId = "t0")
            next().assert(sourceId = "t1")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t3")
            assertNoMoreElements()
        }
    }

    @Test
    fun `tier - local cache`() = runSimpleMediaSelectorTestSuite {
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "t0", kind = WEB),
            media(sourceId = "t1", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t3", kind = WEB),
            media(sourceId = "cache", kind = MediaSourceKind.LocalCache),
        )
        mediaApi.shuffle()
        setSourceTiers(
            "t0" to 0u,
            "t1" to 1u,
            "t2" to 2u,
            "t3" to 3u,
        )
        preferenceApi.preferKind(WEB)

        assertMedias {
            next().assert(sourceId = "cache")
            next().assert(sourceId = "t0")
            next().assert(sourceId = "t1")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t3")
            assertNoMoreElements()
        }
    }

    @Test
    fun `channel tier - channels within one source sorted by channel tier`() = runSimpleMediaSelectorTestSuite {
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "s", kind = WEB, alliance = "channel-b", mediaId = "s.b"),
            media(sourceId = "s", kind = WEB, alliance = "channel-a", mediaId = "s.a"),
            media(sourceId = "s", kind = WEB, alliance = "channel-c", mediaId = "s.c"),
        )
        mediaApi.shuffle()
        setSourceTiers("s" to 1u)
        setChannelTiers(
            "s",
            "channel-a" to 0u,
            "channel-b" to 2u,

        )

        assertMedias {
            next().assert(mediaId = "s.a")
            next().assert(mediaId = "s.c")
            next().assert(mediaId = "s.b")
            assertNoMoreElements()
        }
    }

    @Test
    fun `channel tier - channel tier participates in cross-source sorting`() = runSimpleMediaSelectorTestSuite {

        initSubject()
        mediaApi.addMedia(
            media(sourceId = "B", kind = WEB, alliance = "channel-c", mediaId = "B.c"),
            media(sourceId = "A", kind = WEB, alliance = "channel-a", mediaId = "A.a"),
            media(sourceId = "A", kind = WEB, alliance = "channel-b", mediaId = "A.b"),
        )
        setSourceTiers(
            "A" to 3u,
            "B" to 3u,
        )
        setChannelTiers("A", "channel-a" to 0u, "channel-b" to 0u)
        setChannelTiers("B", "channel-c" to 1u)

        assertMedias {

            next().assert(mediaId = "A.a")
            next().assert(mediaId = "A.b")
            next().assert(mediaId = "B.c")
            assertNoMoreElements()
        }
    }

    @Test
    fun `channel tier - demoted channel ranks after other source`() = runSimpleMediaSelectorTestSuite {

        initSubject()
        mediaApi.addMedia(
            media(sourceId = "A", kind = WEB, alliance = "bad-channel", mediaId = "A.bad"),
            media(sourceId = "B", kind = WEB, alliance = "channel", mediaId = "B.ch"),
        )
        setSourceTiers(
            "A" to 0u,
            "B" to 1u,
        )
        setChannelTiers("A", "bad-channel" to 2u)

        assertMedias {
            next().assert(mediaId = "B.ch")
            next().assert(mediaId = "A.bad")
            assertNoMoreElements()
        }
    }

    @Test
    fun `tier - LAN`() = runSimpleMediaSelectorTestSuite {
        initSubject()
        mediaApi.addMedia(
            media(sourceId = "t0", kind = WEB),
            media(sourceId = "t1", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t2", kind = WEB),
            media(sourceId = "t3", kind = WEB),
            media(sourceId = "local", kind = WEB, location = MediaSourceLocation.Local),
            media(sourceId = "lan", kind = WEB, location = MediaSourceLocation.Lan),
        )
        mediaApi.shuffle()
        setSourceTiers(
            "t0" to 0u,
            "t1" to 1u,
            "t2" to 2u,
            "t3" to 3u,
        )
        preferenceApi.preferKind(WEB)

        assertMedias {
            next().assert(sourceId = "local")
            next().assert(sourceId = "lan")
            next().assert(sourceId = "t0")
            next().assert(sourceId = "t1")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t2")
            next().assert(sourceId = "t3")
            assertNoMoreElements()
        }
    }
}
