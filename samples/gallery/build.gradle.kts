import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The samples gallery, on the desktop and in the browser. Run it with `./gradlew :samples:gallery:run`
// or `./gradlew :samples:gallery:wasmJsBrowserDevelopmentRun`. Never published.
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
    }
}

// detekt's default sources are the JVM `src/main` layout; point it at the multiplatform one.
extensions.configure<DetektExtension> {
    source.setFrom("src/commonMain/kotlin", "src/jvmMain/kotlin", "src/wasmJsMain/kotlin")
}
