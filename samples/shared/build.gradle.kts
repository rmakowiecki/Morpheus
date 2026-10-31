// The sample catalog, its registry and capture recipes, the sample theme and the helpers samples
// share. Consumed by the gallery now and the capture tool later; never published.
plugins {
    id("morpheus.kmp-sample")
    id("org.jetbrains.compose")
}

kotlin {
    android {
        namespace = "com.rossomak.morpheus.samples"
        // Compose resources on Android are packaged as Android assets.
        androidResources { enable = true }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":morpheus"))
            api(libs.jetbrains.compose.foundation)
            api(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.components.resources)
        }
        // JVM only: the pure helpers have no platform in them, and samples run no wasm browser tests.
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotest.assertions.core)
        }
    }
}

compose.resources {
    // Pinned: the default follows the Gradle group and module name.
    packageOfResClass = "com.rossomak.morpheus.samples.resources"
}
