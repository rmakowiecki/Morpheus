// The sample catalog, its registry and capture recipes, the sample theme and the helpers samples
// share. Consumed by the gallery now and the capture tool later; never published.
plugins {
    id("morpheus.kmp-sample")
}

kotlin {
    android {
        namespace = "com.rossomak.morpheus.samples"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":morpheus"))
            api(libs.jetbrains.compose.foundation)
            api(libs.jetbrains.compose.material3)
        }
    }
}
