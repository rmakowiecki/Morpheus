plugins {
    `kotlin-dsl`
}

dependencies {
    // Compiled against only: the root build loads these at runtime, in the classloader every module
    // shares (see the root build.gradle.kts).
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    // Compose Multiplatform: unpacks Skiko's wasm runtime next to the wasmJs test bundle.
    compileOnly(libs.compose.multiplatform.gradlePlugin)
    // Static-analysis plugins applied via pluginManager.apply(...) in morpheus.quality.
    implementation(libs.spotless.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
}
