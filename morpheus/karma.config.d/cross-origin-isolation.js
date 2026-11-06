// Serve the test page cross-origin isolated, so Chrome's performance.now() ticks at about 5 µs instead of
// 100 µs and the benchmarks can time sub-millisecond phases. The page only loads same-origin resources,
// which these headers don't block.
config.set({
    customHeaders: (config.customHeaders || []).concat([
        { match: ".*", name: "Cross-Origin-Opener-Policy", value: "same-origin" },
        { match: ".*", name: "Cross-Origin-Embedder-Policy", value: "require-corp" },
    ]),
});
