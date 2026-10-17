import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

// Shared static-analysis convention: Spotless (ktlint formatting) + detekt (smells) +
// Android Lint baseline wiring. Applied from morpheus.kmp-library and the playground so
// every module gets the same tasks. Plugins applied in the body via pluginManager.apply
// to avoid the precompiled-script-plugin accessor probe (same reason as AGP plugins).
pluginManager.apply("com.diffplug.spotless")
pluginManager.apply("io.gitlab.arturbosch.detekt")

// ktlint rule overrides applied on top of the repo-root .editorconfig. Class and function signature
// wrapping is intentionally author-controlled. In particular, the `class-signature` rule would
// otherwise collapse multiline data-class constructors.
val ktlintOverrides = mapOf(
    "ktlint_standard_class-signature" to "disabled",
    "ktlint_standard_function-signature" to "disabled",
    "ktlint_standard_parameter-list-wrapping" to "disabled",
    "ktlint_standard_argument-list-wrapping" to "disabled",
    "ktlint_standard_parameter-wrapping" to "disabled",
    "ktlint_standard_trailing-comma-on-declaration-site" to "disabled",
    "ktlint_standard_trailing-comma-on-call-site" to "disabled",
)

extensions.configure<SpotlessExtension> {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**")
        ktlint().editorConfigOverride(ktlintOverrides)
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint().editorConfigOverride(ktlintOverrides)
    }
}

extensions.configure<DetektExtension> {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    // Per-module baseline; created by `./gradlew detektBaseline`. Missing file is ignored.
    baseline = file("detekt-baseline.xml")
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "11"
    reports {
        html.required.set(true)
        sarif.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}

// The baseline task is a separate type, so it needs the jvmTarget set independently —
// otherwise detekt derives it from the running JDK (23), which detekt 1.23.x rejects.
tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "11"
}

// Standalone Lint (multiplatform lib modules) writes a missing baseline and fails, so only wire one that
// exists: these modules start with no frozen findings.
pluginManager.withPlugin("com.android.lint") {
    file("lint-baseline.xml").takeIf { it.exists() }?.let { lintBaseline ->
        extensions.configure<com.android.build.api.dsl.Lint>("lint") { baseline = lintBaseline }
    }
}

// Per-module entry point, e.g. `./gradlew :morpheus:staticAnalysis`.
val moduleStaticAnalysis = tasks.register("staticAnalysis") {
    group = "verification"
    description = "Runs Spotless, detekt and (Android modules) Lint for this module."
    dependsOn("spotlessCheck", "detekt")
}
pluginManager.withPlugin("com.android.lint") { moduleStaticAnalysis.configure { dependsOn("lint") } }
