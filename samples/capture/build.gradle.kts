import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The headless capture tool: renders registered samples to the WebP animations and PNG stills in
// docs/media. Run it with `./gradlew :samples:capture:run --args="hero"`, or `--args="all"`. Never
// published and never run in CI; its renderer tests are. See ADR-0002.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.jetbrains.compose)
    id("morpheus.quality")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    implementation(project(":samples:shared"))
    implementation(compose.desktop.currentOs)
    // The scene's LifecycleOwner, which the lib's renderer needs at least STARTED.
    implementation(libs.jetbrains.lifecycle.runtime.compose)
    // Not only for tests: the renderer swaps Dispatchers.Main for an unconfined test dispatcher, so the lib's
    // frame loop resumes inside each render call instead of racing it.
    implementation(libs.kotlinx.coroutines.test)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.assertions.core)
}

compose.desktop {
    application {
        mainClass = "com.rossomak.morpheus.samples.capture.MainKt"
        // The run task starts in this module's folder; the media folder is the repository's.
        jvmArgs += listOf("-Djava.awt.headless=true", "-Dmorpheus.mediaDir=$rootDir/docs/media")
    }
}

tasks.test {
    useJUnitPlatform()
    // One sample's frames are in memory at a time, and they run to a few hundred MB.
    maxHeapSize = "2g"
    systemProperty("java.awt.headless", "true")
    testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
