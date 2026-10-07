package com.wynime.app.domain.media.selector

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import com.wynime.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import com.wynime.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import com.wynime.app.domain.usecase.GlobalKoin
import com.wynime.app.domain.usecase.UseCase
import com.wynime.datasources.api.source.MediaSourceKind
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface MediaSelectorAutoSelectUseCase : UseCase {
    suspend operator fun invoke(session: MediaFetchSession, mediaSelector: MediaSelector)
}

class MediaSelectorAutoSelectUseCaseImpl(
    private val koin: Koin = GlobalKoin,
) : MediaSelectorAutoSelectUseCase, KoinComponent {
    private val getMediaSelectorSettingsFlow: GetMediaSelectorSettingsFlowUseCase by inject()
    private val getMediaSelectorSourceTiers: GetMediaSelectorSourceTiersUseCase by inject()
    private val getPreferredWebMediaSource: GetPreferredWebMediaSourceUseCase by inject()

    override suspend fun invoke(session: MediaFetchSession, mediaSelector: MediaSelector) {
        coroutineScope {
            val settings = getMediaSelectorSettingsFlow().first()

            launch {
                if (settings.autoEnableLastSelected) {
                    val sourceId = mediaSelector.mediaSourceId.finalSelected.first()
                    session.mediaSourceResults.firstOrNull { it.mediaSourceId == sourceId }?.enable()
                }
            }

            val subjectId = session.request.first().subjectId.toIntOrNull()
            MediaAutoSelector(mediaSelector).select(
                session,
                MediaAutoSelector.Config(
                    preferredSourceId = subjectId?.let { getPreferredWebMediaSource(it).first() },
                    web = if (settings.preferKind == MediaSourceKind.WEB) MediaAutoSelector.Web(
                        sourceTiers = getMediaSelectorSourceTiers().first(),
                        fastSelect = settings.fastSelectWebKind,
                        exactMatchAfter = settings.fastSelectWebLowTierToleranceDuration,
                    ) else null,
                    fallbackToOtherKinds = true,
                ),
            )
        }
    }

    override fun getKoin(): Koin = koin
}
