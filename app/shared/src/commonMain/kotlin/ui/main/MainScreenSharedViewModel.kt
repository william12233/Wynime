package com.wynime.app.ui.main

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.wynime.app.ui.foundation.AbstractViewModel
import com.wynime.app.ui.user.SelfInfoStateProducer
import org.koin.core.component.KoinComponent

open class MainScreenSharedViewModel : AbstractViewModel(), KoinComponent {
    val selfInfo = SelfInfoStateProducer(koin = getKoin()).flow

    val networkCheckFailed: Flow<Unit> = emptyFlow()
}
