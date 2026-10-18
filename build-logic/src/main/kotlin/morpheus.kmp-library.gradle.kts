import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Morpheus's Kotlin Multiplatform library modules (Android, desktop JVM and wasm in the browser). Each module sets its own
// `android { namespace }` and its own dependencies; everything shared lives here and in morpheus.kmp-targets. Ids carry a
// `morpheus.` prefix so they never clash with a consuming build's own convention plugins.
plugins {
    id("morpheus.kmp-targets")
}

// Only for wasmJs tests: Compose UI loads Skiko's wasm module at startup, and this plugin puts it in the test bundle.
pluginManager.apply("org.jetbrains.compose")
// The multiplatform Android target gets Lint only through the standalone Lint plugin.
pluginManager.apply("com.android.lint")

private val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<KotlinMultiplatformExtension> {
    // Every public declaration states its visibility and type, so what other modules can depend on is
    // always a decision rather than a default.
    explicitApi()

    // Common tests also run here, in a headless browser.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // Headless Chrome, not Node: Skiko's wasm module only loads in a browser. Each module's
            // karma.config.d drops Chrome's sandbox on CI.
            testTask { useKarma { useChromeHeadless() } }
        }
        // Not an app: webpack only bundles Skiko's runtime into the test bundle for a target with an
        // executable binary (CMP-4906).
        binaries.executable()
    }

    sourceSets.getByName("commonTest").dependencies {
        implementation(kotlin("test"))
        implementation(libs.findLibrary("kotest-assertions-core").get())
    }
}
