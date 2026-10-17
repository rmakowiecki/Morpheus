// CI runners often lack the kernel features Chrome's sandbox needs; the sandbox guards against
// hostile pages, which these tests never load.
if (process.env.CI) {
    config.set({
        browsers: ["ChromeHeadlessCI"],
        customLaunchers: {
            ChromeHeadlessCI: { base: "ChromeHeadless", flags: ["--no-sandbox"] },
        },
    });
}
