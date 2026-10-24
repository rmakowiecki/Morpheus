// The benchmarks only run with MORPHEUS_BENCHMARK set and not empty, and the page can't read the environment: pass it
// through Karma's client config, where Benchmarks.wasmJs.kt reads it.
config.set({
    client: Object.assign({}, config.client, { morpheusBenchmark: !!process.env.MORPHEUS_BENCHMARK }),
});
