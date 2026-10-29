// The samples' Kotlin Multiplatform modules: the same targets and checks as morpheus.kmp-library, but
// snippet-grade code (no explicit API mode), no wasm browser tests and no Android Lint. Never published;
// see ADR-0002.
plugins {
    id("morpheus.kmp-targets")
}

// A sample module that applies the Compose plugin (for resources) gets this check, which wants a wasm executable
// binary so browser tests can load Skiko (CMP-4906). Samples run no wasm browser tests; the gallery is the app.
tasks.matching { it.name == "checkComposeUiTestConfigurationForWasmJs" }.configureEach { enabled = false }
