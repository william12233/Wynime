rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://repo.huaweicloud.com/repository/maven/")
            content { includeGroupByRegex("org\\.jetbrains\\.kotlin(\\..*)?") }
        }
        mavenCentral()
        gradlePluginPortal()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
