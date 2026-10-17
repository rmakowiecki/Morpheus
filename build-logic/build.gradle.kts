plugins {
    `kotlin-dsl`
}

dependencies {
    // Referenced from the plugins{} blocks of the convention scripts, and applied by id from module builds.
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.compose.gradlePlugin)
    // Static-analysis plugins applied via pluginManager.apply(...) in morpheus.quality.
    implementation(libs.spotless.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
}
