# Samples

The samples are never published, so consumers never download demo code. `shared` holds the sample composables and
their registry, `gallery` runs them on desktop and in the browser, and `capture` renders them headlessly to media.

## The viewer notice

Some samples are vivid, so the gallery opens with a notice for viewers sensitive to flashing lights, above the list
and before any sample plays. Its text lives in `ViewerNotice`. The root README and the Pages fallback page in
`gallery/src/wasmJsMain/resources/index.html` quote it, and `ViewerNoticeTest` fails when they stop matching.
[`CONTRIBUTING.md`](../CONTRIBUTING.md) says what to check when you add a sample.

## The gallery's sections

The gallery lists its samples in five sections, in this order, and the registry keeps them sorted the same way:

- **Showcase**: eye-catchers, which earn their place by how they look.
- **Shapes**: the shapes that decide where a layer's nodes sit, and how a layer unites them.
- **Meshes**: how a layer's mesh is built and moves: layers, edge rules, seeds, the window, wave and pulse.
- **Fills**: what paints the nodes and edges, from a solid color to a custom brush.
- **Morphs**: a graph changing from one spec to another.

Every sample has a one-sentence summary for the list and a description of two to four sentences for its pane, both in
the terms of [`CONTEXT.md`](../CONTEXT.md). A new sample goes at the end of its section.

## Refreshing the README's hero

Captures go to `docs/media`, which git ignores. The README's hero is a hand-picked copy in `docs/assets`.

```shell
./gradlew :samples:capture:run --args="hero"
cp docs/media/hero.webp docs/assets/hero.webp
```

The hero's recipe renders at density 2, 24 fps and WebP quality 80 to stay sharp within its 15 MB budget; a quality
above 85 makes the dense mesh several times larger. Capture on an idle machine, and run `--args="all"` only when every
sample fits its budget.
