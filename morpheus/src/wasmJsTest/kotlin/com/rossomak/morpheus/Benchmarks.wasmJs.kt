package com.rossomak.morpheus

/** The page can't read the environment, so karma.config.d/benchmark.js passes the variable through Karma's client config. */
internal actual fun benchmarksEnabled(): Boolean = karmaBenchmarkFlag()

private fun karmaBenchmarkFlag(): Boolean = js("!!(globalThis.__karma__ && globalThis.__karma__.config && globalThis.__karma__.config.morpheusBenchmark)")
