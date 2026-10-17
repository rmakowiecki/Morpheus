plugins {
    id("morpheus.kmp-library")
}

kotlin {
    android {
        namespace = "com.rossomak.morpheus.tuner"
    }

    sourceSets {
        commonMain.dependencies {
            // One dependency for consumers: the tuner brings the engine it edits.
            api(project(":morpheus"))
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.material.icons.core)
            implementation(libs.jetbrains.compose.material.icons.extended)
        }
    }
}
