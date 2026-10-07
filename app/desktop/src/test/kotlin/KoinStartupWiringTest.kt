package com.wynime.app.desktop

import androidx.compose.ui.window.WindowState
import com.wynime.app.data.network.CollectionRemovalService
import com.wynime.app.data.network.SubjectService
import com.wynime.app.data.persistent.database.WynimeDatabase
import com.wynime.app.data.repository.subject.BangumiTrackingSyncRepository
import com.wynime.app.data.repository.subject.SubjectCollectionRepository
import com.wynime.app.data.repository.user.GuestSession
import com.wynime.app.data.repository.user.TokenRepository
import com.wynime.app.data.repository.user.UserRepository
import com.wynime.app.domain.media.fetch.MediaSourceManager
import com.wynime.app.domain.session.SessionManager
import com.wynime.app.domain.sourceplugin.SourcePluginRegistry
import com.wynime.app.platform.DesktopContext
import com.wynime.app.platform.ExtraWindowProperties
import com.wynime.app.platform.getCommonKoinModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class KoinStartupWiringTest {
    @Test
    fun `production startup resolves login collections sources and removal without legacy service`() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("wynime-koin-startup-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val context = DesktopContext(
            WindowState(), directory.resolve("data"), directory.resolve("cache"),
            directory.resolve("logs"), ExtraWindowProperties(),
        )
        val application = startKoin {
            modules(getCommonKoinModule({ context }, scope))
            modules(getDesktopModules({ context }, scope))
        }
        try {
            with(application.koin) {
                assertIs<GuestSession>(get<TokenRepository>().session.first())
                assertNotNull(get<SessionManager>())
                assertNotNull(get<UserRepository>())
                assertNotNull(get<SubjectService>())
                assertNotNull(get<SubjectCollectionRepository>())
                assertNotNull(get<BangumiTrackingSyncRepository>())
                assertNotNull(get<CollectionRemovalService>())
                assertNotNull(get<SourcePluginRegistry>())
                assertNotNull(get<MediaSourceManager>())
            }
        } finally {
            application.koin.getOrNull<SourcePluginRegistry>()?.close()
            scope.cancel()
            application.koin.getOrNull<WynimeDatabase>()?.close()
            stopKoin()
        }
    }
}
