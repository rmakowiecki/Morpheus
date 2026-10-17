import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.spotless)
}

// A consumer that includes this build (`includeBuild("morpheus")`) gets `com.rossomak:<module>`
// substituted from source; the same coordinates resolve from a repository once the modules are published.
allprojects {
    group = "com.rossomak"
    version = "0.1.0-SNAPSHOT"
}

// The root project itself isn't a subproject, so the per-module quality convention never touches its
// own build.gradle.kts/settings.gradle.kts — cover those here directly.
extensions.configure<SpotlessExtension> {
    kotlinGradle {
        target("build.gradle.kts", "settings.gradle.kts")
        ktlint()
    }
}

// Aggregate entry points for static analysis. Not wired into `check` (kept fast for tests).
val staticAnalysis = tasks.register("staticAnalysis") {
    group = "verification"
    description = "Runs Spotless, detekt and Android Lint across all modules."
}
val formatCode = tasks.register("formatCode") {
    group = "formatting"
    description = "Applies Spotless (ktlint) formatting across all modules."
}

gradle.projectsEvaluated {
    staticAnalysis.configure {
        dependsOn(tasks.matching { task -> task.name == "spotlessCheck" })
        dependsOn(subprojects.flatMap { it.tasks.matching { task -> task.name == "spotlessCheck" } })
        dependsOn(subprojects.flatMap { it.tasks.matching { task -> task.name == "detekt" } })
        dependsOn(subprojects.flatMap { it.tasks.matching { task -> task.name == "lint" } })
    }
    formatCode.configure {
        dependsOn(tasks.matching { task -> task.name == "spotlessApply" })
        dependsOn(subprojects.flatMap { it.tasks.matching { task -> task.name == "spotlessApply" } })
    }
}
