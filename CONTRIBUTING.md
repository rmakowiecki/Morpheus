# Contributing

## Checks

Run these before opening a pull request:

```shell
./gradlew build staticAnalysis
```

`./gradlew spotlessCheck detekt` is the quick version for formatting and static analysis alone.

## Running the samples

```shell
./gradlew :samples:gallery:run                     # the desktop gallery
./gradlew :samples:gallery:wasmJsBrowserDevelopmentRun   # the browser gallery
./gradlew :morpheus-playground:run                 # the tuner playground
```

[`samples/README.md`](samples/README.md) says how the captures on the README are made and refreshed.

## Adding a sample

Samples can be vivid, and the gallery, the README and the Pages fallback page carry a notice for viewers sensitive to
flashing lights (`ViewerNotice`). Before you add a sample, check that nothing in it flashes more than three times a
second (WCAG 2.3.1), and that the notice still fits what it shows. If it doesn't, change the notice in `ViewerNotice`,
the README and `index.html` together; `ViewerNoticeTest` fails when they drift apart.

## Vocabulary

[`CONTEXT.md`](CONTEXT.md) is the project's glossary. Use its terms in code, docs and pull requests.
