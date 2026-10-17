import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The desktop host of the network-graph playground, packaged as an app by the Compose Multiplatform
// Gradle plugin. Run it with `./gradlew :morpheus-playground:run`.
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
    implementation(project(":morpheus-tuner"))
    implementation(compose.desktop.currentOs)
    // Dispatchers.Main for the desktop: the lib's renderer runs repeatOnLifecycle, which switches to it.
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.jetbrains.compose.material3)
}

compose.desktop {
    application {
        mainClass = "com.rossomak.morpheus.playground.MainKt"
    }
}
