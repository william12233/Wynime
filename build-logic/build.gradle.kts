import java.util.Properties

plugins {
    `kotlin-dsl`
}

fun rootProperties(fileName: String): Provider<Properties> =
    providers.fileContents(layout.settingsDirectory.dir("..").file(fileName)).asText
        .map { text -> Properties().apply { text.reader().use { load(it) } } }

val rootLocalProperties = rootProperties("local.properties")
val rootGradleProperties = rootProperties("gradle.properties")

fun toolchainProperty(name: String): Provider<String> =
    rootLocalProperties.map { it.getProperty(name) }
        .orElse(rootGradleProperties.map { it.getProperty(name) })
        .orElse(providers.gradleProperty(name))

kotlin {
    jvmToolchain {
        toolchainProperty("jvm.toolchain.vendor").orNull?.let { vendor.set(JvmVendorSpec.matching(it)) }
        toolchainProperty("jvm.toolchain.version").orNull?.let { languageVersion.set(JavaLanguageVersion.of(it)) }
    }
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi")
    }
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.awssdk.s3)
}

dependencies {
    api(gradleApi())
    api(gradleKotlinDsl())

    api(libs.kotlin.gradle.plugin) {
        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
        exclude("org.jetbrains.kotlin", "kotlin-reflect")
    }

    api(libs.android.gradle.plugin)
    api(libs.atomicfu.gradle.plugin)
    api(libs.android.application.gradle.plugin)
    api(libs.android.kotlin.multiplatform.library.gradle.plugin)
    api(libs.compose.multiplatfrom.gradle.plugin)
    api(libs.kotlin.compose.compiler.gradle.plugin)
    api(libs.mannodermaus.android.junit5.gradle.plugin)
    api(libs.compose.stability.analyzer.gradle.plugin)
    implementation(kotlin("script-runtime"))
    implementation(libs.snakeyaml)
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit5.jupiter.api)
    testRuntimeOnly(libs.junit5.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
