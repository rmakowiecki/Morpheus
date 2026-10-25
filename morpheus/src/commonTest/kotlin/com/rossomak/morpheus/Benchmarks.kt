package com.rossomak.morpheus

/** True when `MORPHEUS_BENCHMARK` is set and not empty, as on both platforms, so the benchmarks and reports run; otherwise they return at once. */
internal expect fun benchmarksEnabled(): Boolean
