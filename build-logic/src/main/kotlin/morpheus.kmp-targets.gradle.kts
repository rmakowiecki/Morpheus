import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Internal: the targets every Kotlin Multiplatform module here builds for (Android, desktop JVM and wasm
// in the browser), shared by morpheus.kmp-library and morpheus.kmp-sample so their target settings
// can't drift. Apply one of those, not this.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
pluginManager.apply("morpheus.quality")

extensions.configure<KotlinMultiplatformExtension> {
    (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
}

// detekt's default sources are the Android/JVM `src/main` layout; point it at the multiplatform one.
extensions.configure<DetektExtension> {
    source.setFrom(
        "src/commonMain/kotlin",
        "src/commonTest/kotlin",
        "src/androidMain/kotlin",
        "src/jvmMain/kotlin",
        "src/jvmTest/kotlin",
        "src/wasmJsMain/kotlin",
    )
}
