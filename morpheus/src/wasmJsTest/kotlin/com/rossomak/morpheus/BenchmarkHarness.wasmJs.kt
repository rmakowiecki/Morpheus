package com.rossomak.morpheus

/** The page can't read the environment, so karma.config.d/benchmark.js passes the variable through Karma's client config. */
internal actual fun benchmarksEnabled(): Boolean = karmaBenchmarkFlag()

private fun karmaBenchmarkFlag(): Boolean = js("!!(globalThis.__karma__ && globalThis.__karma__.config && globalThis.__karma__.config.morpheusBenchmark)")

/** The browser, and whether the page is cross-origin isolated: only then does `performance.now()` tick finely, at about 5 µs instead of 100 µs. */
internal actual fun benchmarkPlatform(): String = "wasm in ${userAgent()}, crossOriginIsolated: ${crossOriginIsolated()}"

/** The browser's user agent, which names its version. */
private fun userAgent(): String = js("globalThis.navigator ? globalThis.navigator.userAgent : 'no browser'")

/** Whether the page is cross-origin isolated, as `karma.config.d/cross-origin-isolation.js` asks. */
private fun crossOriginIsolated(): Boolean = js("!!globalThis.crossOriginIsolated")
