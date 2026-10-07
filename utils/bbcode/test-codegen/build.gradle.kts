plugins {
    id("wynime.jvm-library")
    id("wynime.flatten-source-sets")
}

dependencies {
    implementation(projects.utils.bbcode)
    implementation(projects.utils.testing)
    implementation(libs.kotlinpoet)
}
