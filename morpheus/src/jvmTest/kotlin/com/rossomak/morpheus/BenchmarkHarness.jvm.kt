package com.rossomak.morpheus

internal actual fun benchmarksEnabled(): Boolean = !System.getenv("MORPHEUS_BENCHMARK").isNullOrEmpty()

/** The JVM's name and version. */
internal actual fun benchmarkPlatform(): String = "JVM ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}"
