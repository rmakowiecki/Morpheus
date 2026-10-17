// Named apart from a consumer's own build-logic: a composite build needs unique build names.
rootProject.name = "morpheus-build-logic"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
