// The invariant tests run synchronously for seconds at a time on the browser's only thread, so
// the page cannot answer Karma's socket pings until a test returns, and a single test can outlast
// Mocha's 2 s budget. Both defaults would fail a test that is only slow; give them minutes.
config.set({
    pingTimeout: 300000,
    browserDisconnectTimeout: 300000,
    browserNoActivityTimeout: 600000,
});
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 600000 });
