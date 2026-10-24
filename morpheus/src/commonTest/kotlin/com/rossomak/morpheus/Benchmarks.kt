package com.rossomak.morpheus

/** True when `MORPHEUS_BENCHMARK` is set, so the benchmarks and reports run; otherwise they return at once. */
internal expect fun benchmarksEnabled(): Boolean
