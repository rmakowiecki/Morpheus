package com.rossomak.morpheus

internal actual fun benchmarksEnabled(): Boolean = System.getenv("MORPHEUS_BENCHMARK") != null
