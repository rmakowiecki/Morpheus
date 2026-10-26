// The benchmarks only run with MORPHEUS_BENCHMARK set and not empty, and the page can't read the environment: pass it
// through Karma's client config, where BenchmarkHarness.wasmJs.kt reads it.
const morpheusBenchmark = !!process.env.MORPHEUS_BENCHMARK;
config.set({
    client: Object.assign({}, config.client, { morpheusBenchmark: morpheusBenchmark }),
});
// Say so here too, so a run that sets the flag but prints no benchmark header shows the page skipped them.
if (morpheusBenchmark) {
    require("karma/lib/logger").create("morpheus").info("MORPHEUS_BENCHMARK set: the benchmarks run in the page, each printing a header line");
}
