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

### The gallery deploys to GitHub Pages from `develop`

The gallery's browser build is the live demo, deployed on every push to `develop`, so it tracks current
work. CI never runs captures: media changes are intentional commits.

## Considered options

- **Screen recordings for the README.** Rejected: they drift from the code, can't be reproduced, and go
  stale as soon as a spec changes.
- **Samples inside the tuner playground.** Rejected: a showcase and a design tool pull the UI in different
  directions.
- **GIF or lossless WebP.** Rejected: a dense, glowing mesh loses color as a GIF, and both run several times
  the size of lossy WebP.
- **Seamless cycles instead of loop modes.** Rejected: drift is a sum of sines with non-integer frequency
  ratios, so a sample that closes its own cycle still jumps at the seam.
