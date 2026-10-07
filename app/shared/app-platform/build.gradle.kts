import com.android.build.gradle.ProguardFiles.getDefaultProguardFile

plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

    idea
    id("wynime.build-config")
}

val sentryDsn = getPropertyOrNull("wynime.sentry.dsn") ?: ""
val analyticsKey = getPropertyOrNull("wynime.analytics.key") ?: ""

val distroChannel = getPropertyOrNull("wynime.distro.channel") ?: "default"

val currentGitInfo = gitInfo.get()

kotlin {
    android {
        namespace = "com.wynime.app.platform"

        optimization {
            minify = false
            keepRules.apply {
                files(
                    getDefaultProguardFile("proguard-android-optimize.txt", layout.buildDirectory),
                    *sharedAndroidProguardRules(),
                )
            }
        }
    }

    sourceSets.commonMain.dependencies {
        api(projects.utils.platform)
        api(projects.app.shared.appLang)
        api(projects.datasource.datasourceApi)
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.datetime)
        api(libs.kotlinx.collections.immutable)

        api(libs.compose.lifecycle.viewmodel.compose)
        api(libs.compose.lifecycle.runtime.compose)
        api(libs.compose.navigation.compose)
        api(libs.compose.navigation.runtime)
        api(libs.compose.navigation3.runtime)
        api(libs.kotlinx.serialization.json)
        api(libs.compose.material3.adaptive.core)
        api(libs.compose.material3.adaptive.layout)
        api(libs.compose.material3.adaptive.navigation0)

        api(libs.koin.core)
        api(projects.utils.analytics)
    }
    sourceSets.commonTest.dependencies {
        implementation(projects.utils.uiTesting)
        implementation(libs.turbine)
    }
    sourceSets.androidMain.dependencies {
        api(projects.utils.buildConfig)
    }
    sourceSets.desktopMain.dependencies {
        api(libs.jna)
        api(libs.jna.platform)
        api(compose.desktop.currentOs)
    }

}

buildConfig {
    packageName.set("com.wynime.app.platform")
    className.set("WynimeBuildConfig")
    outputDir.set(layout.buildDirectory.dir("generated/buildconfig"))

    fun BuildConfigPlatform.firebaseFields() {
        fun getProp(name: String): String {
            return if (enableFirebase) {
                getProperty(name).also {
                    check(it.isNotBlank()) { "Local property '$name' is not set. You must either set it or disable `wynime.enable.firebase`." }
                }
            } else {
                ""
            }
        }
        stringField("firebaseGAAppId", getProp("firebase.ga.app.id"), isOverride = false)

        stringField("firebaseGAApiSecret", getProp("firebase.ga.api.secret"), isOverride = false)

        booleanField("analyticsEnabled", enableFirebase)
    }

    fun BuildConfigPlatform.gitFields() {
        stringField("gitBranch", currentGitInfo.branch)
        stringField("gitCommitSha", currentGitInfo.commitSha)
        stringField("gitCommitTime", currentGitInfo.commitTime)
    }

    platform("desktop") {
        stringField("versionName", project.version.toString())
        expressionField(
            "isDebug",
            "System.getenv(\"WYNIME_DEBUG\") == \"true\" || System.getenv(\"ANI_DEBUG\") == \"true\" || System.getProperty(\"wynime.debug\") == \"true\"",
        )
        stringField("sentryDsn", sentryDsn)
        stringField("distroChannel", distroChannel)
        gitFields()

        firebaseFields()
    }

    platform("android") {
        stringField("versionName", project.version.toString())
        expressionField("isDebug", "com.wynime.buildconfig.AndroidBuildConfig.DEBUG")
        stringField("sentryDsn", sentryDsn)
        stringField("distroChannel", distroChannel)
        gitFields()

        booleanField("analyticsEnabled", enableFirebase)
    }

}
