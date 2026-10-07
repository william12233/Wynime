package com.wynime.android

import android.app.Application
import android.util.Log
import com.wynime.app.BuildConfig
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import dev.gitlive.firebase.initialize
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.wynime.android.provider.ExternalContentProviderFactoryImpl
import com.wynime.app.data.repository.user.SettingsRepository
import com.wynime.app.data.repository.user.UserRepository
import com.wynime.app.platform.AndroidLoggingConfigurator
import com.wynime.app.platform.AppStartupTasks
import com.wynime.app.platform.JvmLogHelper
import com.wynime.app.platform.StartupTimeMonitor
import com.wynime.app.platform.StepName
import com.wynime.app.platform.create
import com.wynime.app.platform.createAppRootCoroutineScope
import com.wynime.app.platform.getCommonKoinModule
import com.wynime.app.platform.startCommonKoinModule
import com.wynime.app.platform.trace.recordAppStart
import com.wynime.app.ui.settings.tabs.log.getLogsDir
import com.wynime.utils.analytics.Analytics
import com.wynime.utils.analytics.AnalyticsConfig
import com.wynime.utils.analytics.AnalyticsImpl
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.logging.error
import com.wynime.utils.logging.logger
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.openani.mediamp.ffmpeg.FFmpegKit
import java.nio.file.Paths
import kotlin.uuid.ExperimentalUuidApi

class WynimeApplication : Application() {

    companion object {
        init {
            if (BuildConfig.DEBUG) {
                System.setProperty("kotlinx.coroutines.debug", "on")
                System.setProperty("kotlinx.coroutines.stacktrace.recovery", "true")
            }

        }

        lateinit var instance: Instance
            private set

    }

    inner class Instance()

    @OptIn(ExperimentalUuidApi::class)
    override fun onCreate() {
        super.onCreate()
        val startupTimeMonitor = StartupTimeMonitor()

        val logsDir = applicationContext.getLogsDir().absolutePath
        AndroidLoggingConfigurator.configure(logsDir)
        AppStartupTasks.printVersions()
        startupTimeMonitor.mark(StepName.Logging)

        val defaultUEH = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            logger<WynimeApplication>().error(e) { "!!!WYNIME FATAL EXCEPTION!!! ($e)" }
            Thread.sleep(500)
            defaultUEH?.uncaughtException(t, e)
        }
        startupTimeMonitor.mark(StepName.UncaughtExceptionHandler)

        instance = Instance()

        val scope = createAppRootCoroutineScope()

        startupTimeMonitor.mark(StepName.WindowAndContext)

        scope.launch(Dispatchers.IO_) {
            runCatching {
                JvmLogHelper.deleteOldLogs(Paths.get(logsDir))
            }.onFailure {
                Log.e("WynimeApplication", "Failed to delete old logs", it)
            }
        }

        OkHttp

        startKoin {
            androidContext(this@WynimeApplication)
            modules(getCommonKoinModule({ this@WynimeApplication }, scope))

            modules(getCommonAndroidModules(scope))
            modules(getAndroidModules())
        }.startCommonKoinModule(this@WynimeApplication, scope)
        startupTimeMonitor.mark(StepName.Modules)

        val koin = getKoin()
        val analyticsInitializer = scope.launch {
            val settingsRepository = koin.get<SettingsRepository>()
            val userRepository = koin.get<UserRepository>()
            val settings = settingsRepository.analyticsSettings.flow.first()
            settingsRepository.analyticsSettings.update { settings }
            if (settings.isBugReportEnabled) {
                AppStartupTasks.initializeSentry(settings.deviceId)
            }
            if (settings.isAnalyticsEnabled) {
                AppStartupTasks.initializeAnalytics {
                    AnalyticsImpl(
                        AnalyticsConfig.create(),
                    ).apply {
                        Firebase.initialize(applicationContext)
                        init()

                        userRepository.selfInfoFlow.first()?.id?.let {
                            Firebase.analytics.setUserId(it.toString())
                        }
                        scope.launch {
                            userRepository.selfInfoFlow.map { it?.id }.collect {
                                Firebase.analytics.setUserId(it?.toString())
                            }
                        }
                    }
                }
            }
        }

        runBlocking { analyticsInitializer.join() }
        ExternalContentProviderFactoryImpl.initializeApp(this)
        startupTimeMonitor.mark(StepName.Analytics)
        FFmpegKit.initialize(this)
        FFmpegKit.useDefaultRuntimeLibraryDirectory()

        scope.launch {
            Analytics.recordAppStart(startupTimeMonitor)
        }
    }

}
