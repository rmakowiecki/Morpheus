# Morpheus libraries are app-agnostic Kotlin Multiplatform modules

> Status: accepted

## Context

The animated network-graph background began as a prototype inside the Flashcards app's debug tools,
then moved into app-agnostic `lib/` modules there. It is meant for more than one consumer: the Android
app, its Compose Multiplatform website and its store screenshots. So it now lives in this repository,
which consumers include as a Gradle composite build until the modules are published.

## Decision

### Kotlin Multiplatform modules under `com.rossomak.morpheus`

`:morpheus` is the first library, published as `com.rossomak:morpheus` in the package
`com.rossomak.morpheus`. Library modules apply the `morpheus.kmp-library` convention plugin:

- targets: Android and desktop JVM;
- strict explicit API mode;
- JetBrains Compose Multiplatform artifacts through the version catalog, with no `org.jetbrains.compose`
  Gradle plugin;
- the usual Spotless, detekt and Lint.

The one exception is `:morpheus-playground`, a desktop app that hosts the tuner: it is the only
module that applies the `org.jetbrains.compose` plugin, which it needs to package a desktop application.
Nothing depends on it.

Diagnostics and tooling hooks sit behind the `@ExperimentalMorpheusGraphApi` opt-in, so app modules don't
come to depend on them by accident.

### Nothing here knows about a consuming app

A library module carries no brand colors, no app `MaterialTheme` tokens and no app logging. A consumer
applies its branding at its call site, for example by mapping its component styles to an inherited
fill. The library's presets set no fills for the same reason, and branded variants stay in the consumer.

### Color comes from an inherited, lerpable fill

A layer paints with its own `MorpheusGraphLayerSpec.fill`, else its spec's `MorpheusGraphSpec.fill`, else
the fill passed to `morpheusGraphBackground`, which defaults to the `LocalMorpheusGraphFill` composition
local. One fill set once colors every graph, and a single spec or layer can still override it.

`MorpheusGraphFill` is `Solid`, `Linear` or `Radial`, all plain values with geometry in fractions of the
surface, or `Custom`, a `Brush` asked once per layer and frame and once per end of a running morph.

- **Plain values** interpolate during a morph: colors in Oklab, gradient stops matched by position,
  angle, center and radius eased between the ends. A solid end acts as a one-color gradient, and a
  linear end morphing into a radial one passes through a single color half way.
- **`Custom`** can be any brush, animated shaders included. The layer is drawn in white into its
  offscreen layer, then recolored with `BlendMode.SrcIn`, so each element keeps its own alpha. A morph
  involving a custom fill draws the far end over the near one with `BlendMode.SrcAtop` at the morph
  fraction, a crossfade that works for any brush, with both ends still animating.

## Considered options

- **A `Color`-only palette.** Rejected: callers need gradient-fed graphs on plain backgrounds.
- **Only a `Brush`-returning palette**, crossfading every morph. Rejected: a crossfade between two
  gradients reads as two layers, not one gradient moving, and leaves no way to set a color once for
  every graph while overriding it for one.
- **Material3 in the engine.** Rejected: the engine depends only on Compose runtime, UI and animation.
  The tuning UI, which does use Material3, goes in a separate lib module that consumers opt into.
