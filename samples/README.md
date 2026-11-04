# Samples

The samples are never published, so consumers never download demo code. `shared` holds the sample composables and
their registry, `gallery` runs them on desktop and in the browser, and `capture` renders them headlessly to media.

## The viewer notice

Some samples are vivid, so the gallery opens with a notice for viewers sensitive to flashing lights, above the list
and before any sample plays. Its text lives in `ViewerNotice`. The root README and the Pages fallback page in
`gallery/src/wasmJsMain/resources/index.html` quote it, and `ViewerNoticeTest` fails when they stop matching.
[`CONTRIBUTING.md`](../CONTRIBUTING.md) says what to check when you add a sample.

## The root README's code

The two **Start here** samples, `quick-start` and `quick-start-morph`, are the README's quick-start code. The static
snippet is the sample verbatim. The morph snippet is its body with the sample's cycle clock replaced by a state the
app flips. When you change either sample, change the README's snippet with it.

## Refreshing the README's hero

Captures go to `docs/media`, which git ignores. The README's hero is a hand-picked copy in `docs/assets`.

```shell
./gradlew :samples:capture:run --args="hero"
cp docs/media/hero.webp docs/assets/hero.webp
```

The hero's recipe renders at density 2, 24 fps and WebP quality 80 to stay sharp within its 15 MB budget; a quality
above 85 makes the dense mesh several times larger. Capture on an idle machine, and run `--args="all"` only when every
sample fits its budget.
