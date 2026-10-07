import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin

group = "com.wynime"
version = providers.gradleProperty("version.name").get()

val libs = versionCatalogs.named("libs")

val jnaVersion = libs.findVersion("jna").get().requiredVersion
configurations.configureEach {
    resolutionStrategy.force(
        "net.java.dev.jna:jna:$jnaVersion",
        "net.java.dev.jna:jna-platform:$jnaVersion",
    )
}

configureEncoding()
runConnectedDeviceTestsExclusively()

var kotlinConventionsConfigured = false
plugins.withType(KotlinBasePlugin::class.java) {
    if (kotlinConventionsConfigured) return@withType
    kotlinConventionsConfigured = true

    configureKotlinOptIns()
    configureKotlinTestSettings()
    configureJvmTarget()
}

pluginManager.withPlugin("org.jetbrains.compose") {
    pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
        configureComposePreviewToolingDependency()
    }
}
