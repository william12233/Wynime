package com.wynime.app.domain.media.cache

import androidx.compose.runtime.Stable
import com.wynime.app.tools.Progress
import com.wynime.app.tools.toProgress
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.megaBytes
import com.wynime.utils.platform.annotations.TestOnly
import kotlin.random.Random

@Stable
sealed class EpisodeCacheStatus {

    @Stable
    data class Cached(
        val totalSize: FileSize,
    ) : EpisodeCacheStatus()

    @Stable
    data class Caching(

        val progress: Progress,
        val totalSize: FileSize,
    ) : EpisodeCacheStatus()

    @Stable
    data object NotCached : EpisodeCacheStatus()

    companion object {
        @TestOnly
        fun random(random: Random): EpisodeCacheStatus {
            return when (random.nextInt(3)) {
                0 -> Cached(random.nextLong(100L, 500L).megaBytes)
                1 -> Caching(
                    progress = random.nextFloat().toProgress(),
                    totalSize = random.nextLong(100L, 500L).megaBytes,
                )

                else -> NotCached
            }
        }

        @TestOnly
        fun randomOrNull(random: Random): EpisodeCacheStatus? {
            if (random.nextBoolean()) {
                return null
            }
            return random(random)
        }
    }
}

@Stable
fun EpisodeCacheStatus.isCachedOrCaching(): Boolean {
    return this is EpisodeCacheStatus.Cached || this is EpisodeCacheStatus.Caching
}
