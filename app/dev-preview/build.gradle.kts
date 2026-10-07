import org.gradle.api.tasks.JavaExec

plugins { id("wynime.kmp-compose") }

kotlin {
    android { namespace = "com.wynime.preview" }
    sourceSets.commonMain.dependencies {
        implementation(projects.app.shared)
        implementation(projects.utils.uiPreview)
        implementation(libs.mediamp.test)
    }
    sourceSets.desktopMain.dependencies {
        implementation(libs.mediamp.mpv)
        when (getOsTriple()) {
            "windows-x64" -> runtimeOnly(libs.mediamp.mpv.runtime.windows.x64)
            "windows-arm64" -> runtimeOnly(libs.mediamp.mpv.runtime.windows.arm64)
        }
    }
}

val desktopPreview = kotlin.targets.getByName("desktop").compilations.getByName("main")
tasks.register<JavaExec>("runMpvVerification") {
    dependsOn(desktopPreview.compileTaskProvider)
    classpath = desktopPreview.output.allOutputs + desktopPreview.runtimeDependencyFiles!!
    mainClass.set("com.wynime.preview.MpvVerifyKt")
    providers.gradleProperty("wynime.preview.video").orNull?.let {
        systemProperty("wynime.seekverify.video", it)
    }
}
