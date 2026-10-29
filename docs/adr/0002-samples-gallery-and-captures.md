# Samples live in the repository, never published, and captures render only from code

> Status: accepted

## Context

Morpheus is an eye-candy library, but nothing in the repository showed what it does: the README was all
text, and the only runnable host was the tuner playground, a tool for designing specs. The README needs
still images and animations that can be regenerated as specs change, a live demo, and a place to preview
and tweak a demo before capturing it.

## Decision

### Samples are built with the libraries and never published

A top-level `samples` directory holds `samples:shared` (the sample composables, their registry and capture
recipes, the shared sample theme and helpers), `samples:gallery` (a desktop and browser app that runs
them) and, later, `samples:capture` (a headless JVM tool that renders them to media). All are included in
the root build, so pull-request CI compiles and checks them, and an API change that breaks a sample fails
right away. None is published, so consumers never download demo code.

`samples:shared` applies `morpheus.kmp-sample`: the same targets and checks as `morpheus.kmp-library`, both
taken from the internal `morpheus.kmp-targets`, but without explicit API mode, browser tests or Android
Lint. It keeps the Android target so an Android gallery can come later without restructuring. The gallery
is separate from the tuner playground: the authoring tool and the showcase serve different audiences.

### Sample code stays snippet-grade

A sample is one composable plus metadata in the registry, the one ordered list the gallery and the capture
tool both read. The composable is short, idiomatic code a README can quote: no gallery chrome, its tunable
specs as plain parameters with defaults, and specs read so that tuning one never restarts a running cycle.
Titles, controls, theme and background come from the host. Cycles follow the frame clock, never `delay`,
so they advance with a capture's virtual clock.

Driver helpers, such as `MorphCycle`, which seeks a morph state around a list of specs, are exempt from that
rule: the library calls inside them are what a sample shows, so a sample's own code stays a few lines and
quotes the helper's call rather than its body.

### The gallery shows what the library displays, never app wiring

A sample shows what the library draws and how it manipulates the visuals: specs, layers, morphs, fills and
their parameters. It doesn't show how an app feeds the library, such as reacting to swipes or scroll
position, so the gallery has no pager or scroll samples. The gallery's panes are one common set (theme, preview
canvas, Tune, Pause, frame stats, comet debug), and a sample takes only the modules that apply to it: only a
sample with morphs has a scrub slider, comet debug and timing controls.

### Captures render only from code

The capture tool renders each sample at its default specs, headlessly, on a virtual frame clock, so every
run produces identical frames. It never reads the gallery's tuned specs: the tuner's Export copies a spec as
Kotlin, and pasting it into the sample is how a tuned spec reaches a capture. Gallery and capture share the
sample theme and a canvas-sized window reference, so the preview looks like the capture.

### Animated media is lossy WebP under a hard size budget

Animations are lossy WebP encoded by `img2webp`, stills are PNG, in light and dark variants unless a sample
is dark-only. Every file has a byte budget, 5 MB by default; a capture over it fails and names the levers
(frame rate, canvas area, duration, quality). Node drift never repeats, so a capture closes its loop with a
crossfade (the default, for directional motion), a boomerang (for morphs and scrolls) or a single still.

### Captured media is regenerated, not committed

The capture tool writes into `docs/media`, which git ignores. Media is regenerated on demand with
`./gradlew :samples:capture:run` and never checked in, so the repository's history doesn't grow by
megabytes of binaries every time a spec or a default changes (a 6 s loop is a couple of megabytes).

The few images the README and the Pages fallback show are the exception: hand-picked copies of captures,
committed in `docs/assets`. A recapture never touches them; refreshing one is a deliberate copy from
`docs/media`, so binary history grows only when a published image changes, not on every capture. The Pages
fallback loads them by their raw GitHub URL on `develop`, as the Pages artifact holds only the gallery.

### The gallery deploys to GitHub Pages from `develop`

The gallery's browser build is the live demo, deployed on every push to `develop`, so it tracks current
work. CI never runs captures.

## Considered options

- **Committing captures to the repository.** Rejected: every recapture adds megabytes of binary history
  that no clone can shed, and the captures are reproducible from the code anyway.
- **Screen recordings for the README.** Rejected: they drift from the code, can't be reproduced, and go
  stale as soon as a spec changes.
- **Samples inside the tuner playground.** Rejected: a showcase and a design tool pull the UI in different
  directions.
- **GIF or lossless WebP.** Rejected: a dense, glowing mesh loses color as a GIF, and both run several times
  the size of lossy WebP.
- **Seamless cycles instead of loop modes.** Rejected: drift is a sum of sines with non-integer frequency
  ratios, so a sample that closes its own cycle still jumps at the seam.
