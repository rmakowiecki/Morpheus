plugins {
    id("morpheus.kmp-library")
}

kotlin {
    android {
        namespace = "com.rossomak.morpheus"
    }

    // Desktop and wasm both draw through Skia, so they share skikoMain; Android draws through its own canvas.
    applyDefaultHierarchyTemplate {
        common {
            group("skiko") {
                withJvm()
                withWasmJs()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.jetbrains.compose.runtime)
            api(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.ui.util)
            implementation(libs.jetbrains.compose.animation.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        // Skia's native runtime, for the tests that draw a layer onto an image.
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

// See StillFrameAllocationTest: it runs on its own, with escape analysis off.
val allocationTests = "**/StillFrameAllocationTest*"
val jvmTest = tasks.named<Test>("jvmTest") {
    exclude(allocationTests)
}
val jvmAllocationTest = tasks.register<Test>("jvmAllocationTest") {
    description = "Checks that a still frame allocates nothing, with escape analysis off."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath
    include(allocationTests)
    jvmArgs("-XX:-DoEscapeAnalysis")
    testLogging.showStandardStreams = true
}
tasks.named("check") {
    dependsOn(jvmAllocationTest)
}
