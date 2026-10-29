import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The samples gallery, on the desktop and in the browser. Run it with `./gradlew :samples:gallery:run`
// or `./gradlew :samples:gallery:wasmJsBrowserDevelopmentRun`. Never published.
//
// Benchmarking (docs/benchmark.md): `./gradlew :samples:gallery:run -Pbenchmark` turns off the frame-rate cap
// and sets device scale 2. In the browser, serve the gallery with `wasmJsBrowserDevelopmentRun` (or
// `wasmJsBrowserProductionRun`) and open it in a Chrome started with
// `--disable-gpu-vsync --disable-frame-rate-limit --force-device-scale-factor=2`.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("morpheus.quality")
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        // index.html loads the bundle by this name.
        outputModuleName.set("gallery")
        browser {
            commonWebpackConfig { outputFileName = "gallery.js" }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":samples:shared"))
            implementation(project(":morpheus-tuner"))
            implementation(libs.jetbrains.compose.material.icons.extended)
            // The lifecycle the lib's renderer reads, which pausing a sample holds below STARTED.
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            // Dispatchers.Main for the desktop: the lib's renderer runs repeatOnLifecycle, which switches to it.
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.rossomak.morpheus.samples.gallery.MainKt"
        if (providers.gradleProperty("benchmark").isPresent) {
            // No vsync, so the frame interval is the real cost per frame, at the recipe's device scale.
            jvmArgs += listOf("-Dskiko.vsync.enabled=false", "-Dsun.java2d.uiScale=2")
        }
    }
}

// detekt's default sources are the JVM `src/main` layout; point it at the multiplatform one.
extensions.configure<DetektExtension> {
    source.setFrom("src/commonMain/kotlin", "src/jvmMain/kotlin", "src/wasmJsMain/kotlin")
}
