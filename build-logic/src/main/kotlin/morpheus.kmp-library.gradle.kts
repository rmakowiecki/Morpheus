import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Morpheus's Kotlin Multiplatform library modules (Android, desktop JVM and wasm in the browser). Each module sets its own
// `android { namespace }` and its own dependencies; everything shared lives here. Ids carry a
// `morpheus.` prefix so they never clash with a consuming build's own convention plugins.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
// Only for wasmJs tests: Compose UI loads Skiko's wasm module at startup, and this plugin puts it in the test bundle.
pluginManager.apply("org.jetbrains.compose")
pluginManager.apply("morpheus.quality")
// The multiplatform Android target gets Lint only through the standalone Lint plugin.
pluginManager.apply("com.android.lint")

private val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<KotlinMultiplatformExtension> {
    // Every public declaration states its visibility and type, so what other modules can depend on is
    // always a decision rather than a default.
    explicitApi()

    (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    // For Compose Multiplatform web consumers; common tests also run here, in a headless browser.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // Headless Chrome, not Node: Skiko's wasm module only loads in a browser. Each module's
            // karma.config.d drops Chrome's sandbox on CI.
            testTask { useKarma { useChromeHeadless() } }
        }
    }

    sourceSets.getByName("commonTest").dependencies {
        implementation(kotlin("test"))
        implementation(libs.findLibrary("kotest-assertions-core").get())
    }
}

// detekt's default sources are the Android/JVM `src/main` layout; point it at the multiplatform one.
extensions.configure<DetektExtension> {
    source.setFrom(
        "src/commonMain/kotlin",
        "src/commonTest/kotlin",
        "src/androidMain/kotlin",
        "src/jvmMain/kotlin",
        "src/wasmJsMain/kotlin",
    )
}
