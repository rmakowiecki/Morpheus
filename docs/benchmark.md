# Benchmark recipe

How to measure the graph renderer's cost, on desktop and in the browser, in two tiers:

- **The recipe**, in the gallery: the whole frame, drawing included, as someone watching it sees it.
  Run it by hand, or in the browser with [a script](#browser-scripted).
- **The [headless benchmarks](#headless-benchmarks)**: the frame's CPU phases (shape, mesh, edge fades,
  alpha settle) without drawing, as tests on the JVM and in headless Chrome. They are quicker to run
  and steadier, but they don't cover drawing.

Which tier a pull request that claims a performance change reports depends on what it changes:

- **A change to the CPU phases** (shape, mesh, edge fades, alpha settle) gates on the headless numbers
  in Chrome, reports the headless JVM numbers from the same run, and reports the recipe's wasm frame
  time once.
- **A change to drawing** gates on the recipe on wasm.
- **The per-frame allocations** (#42) gate on their zero-allocation test, not on timings.
- **JVM recipe timings** are reported only by #33, not per pull request.

Either way it reports before and after numbers, as [Comparing a change](#comparing-a-change) says, and
links here rather than repeating the steps.

## What is measured

- **Sample:** "Pills to ribbons" (the hero) at its default specs. Don't tune it, or press Reset to
  defaults first. "Comet playground" is the second benchmark sample: its front mesh is denser (density
  100) and it sends far more comets, so changes to comet and morph code show there that the hero is too
  light to show. Measure it the same way, on the same 16:9 canvas, and report its rows next to the hero's.
- **Canvas:** the gallery's **16:9** preview, 640×360 dp, with **Scale to fit off**, at **device
  scale 2**: 1280×720 physical pixels. Browser zoom is at 100%.
- **No frame-rate cap:** with vsync on, the frame interval stops at the display's refresh interval
  once a frame costs less than that, and hides the cost. The flags below turn it off, so the frame
  interval is the real cost per frame.
- **Over a full loop:** the hero's loop is 10 s, pills to ribbons and back; the playground's is 12.75 s,
  frame to blooms to cross and back to frame. Read the numbers as they settle over a loop, not from a
  single half-second window.

## The readout

The gallery's control row has a button reading `frame 9.8 ms · graphs 6.1 ms`, refreshed twice a
second. Clicking it opens the breakdown panel, in the side column, under the Tune sheet when that is
open (under the preview in a narrow window). The button and the panel dim from a reset, such as a
sample change, until the next reading arrives:

- **frame** is the average interval between frames; **graphs** is the CPU time spent recording the
  graphs into the display list, every graph on the canvas summed.
- The **phase table** splits the graphs' time per frame into shape, mesh, edge fades, alpha settle,
  fill, edges, halos, dots and composite, in total and per spec layer, back to front.
- The **counts** are per frame (nodes, visible nodes, drawn edges, halos and dots, draw calls) or per
  half-second window (settles, morph meshes, meshes). Edges and dots are drawn in point batches, and
  each batch is one draw call: one per alpha level for the edges, and for the dots one punch per
  size bin and one add per size bin and alpha bucket.
- The **switches** skip drawing edges, halos, dots or the fill recolor, or show violations. A skipped
  step records no time and no draw calls; its raster and GPU cost shows as the drop in **frame**.

The button flags a reading that isn't a benchmark number:

- **capped**: the frame interval sits at a display refresh interval while the graphs take well under it
  (less than 70%), so the frame is waiting for the display and the cap is still on.
- **canvas scaled**: the canvas doesn't show at its own size. Make the window larger.
- **debug switches on**: one of the switches is on.

Phases under the timer's resolution read 0.00 until enough frames add up: in the browser,
`performance.now()` ticks at about 100 µs. The half-second window averages that out.

## Running it

### Desktop (JVM)

1. Run `./gradlew :samples:gallery:run -Pbenchmark`, or the "Gallery (benchmark)" run configuration in
   the IDE. The property adds `-Dskiko.vsync.enabled=false -Dsun.java2d.uiScale=2`.
2. Maximize the window, at least about 1,370 dp wide: the sample list (320 dp), the 640 dp canvas with
   its padding and the 380 dp side column.

### Browser (wasm)

1. Run `./gradlew :samples:gallery:wasmJsBrowserProductionRun`. Note the URL it serves on,
   `http://localhost:8080/` by default.
2. Start a separate Chrome with the flags. They only apply to a new Chrome process, hence the
   separate profile directory. On macOS:

   ```sh
   open -na "Google Chrome" --args \
     --user-data-dir="$TMPDIR/morpheus-benchmark" \
     --disable-gpu-vsync --disable-frame-rate-limit --force-device-scale-factor=2 \
     http://localhost:8080/
   ```

   On Linux or Windows, pass the same flags to the `google-chrome` or `chrome.exe` binary.
3. Maximize the window, at least about 1,370 dp wide, and check that browser zoom is at 100%.

### Browser, scripted

```sh
python3 scripts/gallery_benchmark.py [--sample hero|comet-playground] [--loops 3] [--skip edges,halos,dots,fill|all] [--skip-build] [--chrome PATH]
```

It builds the production bundle, serves it, and opens it in a separate Chrome with the flags above and
`?benchmark`. That opens "Pills to ribbons" on the 16:9 preview with Scale to fit off, and the gallery
prints each half-second reading to the console as a `morpheus-benchmark` line. The script reads them
from Chrome's log, skips a 6 s warm-up, and prints the frame, graphs and phase-table totals per frame,
per loop and as the median of the loops. `--sample comet-playground` opens the playground instead,
through `&sample=comet-playground`, and measures over its 12.75 s loops. It warns about readings
flagged capped, canvas scaled or debug switches on. A Chrome window opens for the run: leave it in
front and the machine otherwise idle, or Chrome slows it down. It uses `$CHROME_BIN` when set, else
Google Chrome.

One run measures one row of the [report](#reporting). Without `--skip` it is the row with every switch
off. `--skip halos` opens the gallery with `?benchmark&skip=halos` and measures the skip halos row, and
so on; `--skip all` turns every skip switch on, for the all skips on row. With `--skip`, every reading
must show its switches on, or the script stops: the bundle didn't read `&skip=`.

```sh
python3 scripts/gallery_benchmark.py
python3 scripts/gallery_benchmark.py --skip-build --skip edges
python3 scripts/gallery_benchmark.py --skip-build --skip halos
python3 scripts/gallery_benchmark.py --skip-build --skip dots
python3 scripts/gallery_benchmark.py --skip-build --skip fill
python3 scripts/gallery_benchmark.py --skip-build --skip all
python3 scripts/gallery_benchmark.py --skip-build --sample comet-playground
```

### Then, on either platform

1. Open "Pills to ribbons", or "Comet playground" for its rows. Press Reset to defaults if it was tuned.
2. Pick the **16:9** preview and turn **Scale to fit** off.
3. Open the breakdown panel by clicking the frame time button in the control row. It opens in the side
   column; open the Tune sheet as well if the numbers should cover tuning, and the two share the column.
   Neither takes canvas height or width from the other.
4. Check that the button says neither "capped" nor "canvas scaled".
5. With every switch off, watch a full loop and record frame, graphs and the phase table.
6. Turn on each skip switch in turn, alone, and record the same over a loop.
7. Turn on every skip switch at once and record the same over a loop.

In the browser, [the script](#browser-scripted) runs steps 5 to 7 with `--skip`.

Changing the sample, theme or preview, editing a spec, pausing or resuming, or flipping a switch
starts a fresh window, so no reading mixes two setups.

## Reporting

Report the recipe's table in the pull request, for wasm, and for the JVM only in #33 (see the tiers above):

| Setup              | frame ms | graphs ms | edges ms | halos ms | dots ms | fill ms |
|--------------------|----------|-----------|----------|----------|---------|---------|
| all switches off   |          |           |          |          |         |         |
| skip edges         |          |           |          |          |         |         |
| skip halos         |          |           |          |          |         |         |
| skip dots          |          |           |          |          |         |         |
| skip fill recolor  |          |           |          |          |         |         |
| all skips on       |          |           |          |          |         |         |
| playground         |          |           |          |          |         |         |

The playground row is "Comet playground" with every switch off; its skip rows are optional. Add the
machine, OS, and browser or JDK version, and the full phase table for the baseline row.

## Headless benchmarks

The second tier: tests that run the frame's CPU phases without drawing, on the JVM and in headless
Chrome, with no window and no one watching. Run them with:

```sh
python3 scripts/headless_benchmark.py [--rounds 3] [--platform jvm|wasm|both] [--tests SUBSTRING]
```

It runs each benchmark test alone, in its own Gradle test run, so each gets a fresh JVM or a fresh Karma
page; see [why alone](#why-each-test-runs-alone). It prints each run's lines as soon as that run finishes,
so a later failure never loses them. With more than one round it interleaves the tests and ends by
printing each line's numbers as their range over the rounds, low-high; a test that failed some rounds
gets the range over the rest, headed `range over 2 of 3 rounds:`. A run that fails, as when Karma's
600 s limit stops a slow test, is listed at the end and the other runs go ahead; the script then exits
non-zero. Ctrl-C ends the runs and, with more than one round, still prints the end summary. On wasm,
set `CHROME_BIN` to a Chrome or Chrome for Testing binary if Karma can't find one.

They print:

- **`SettleBenchmark`, showcase loop:** the showcase's loop replayed as the gallery runs it (below), with
  the shape, mesh, edge fades and alpha settle phases, every layer summed. It prints them for the frame
  where the morph starts or ends, and as a mean over the half second from there, the window the readout
  averages over, which dilutes a one-frame spike such as a morph's start. Then it prints the mean over
  the whole loop, which compares with the gallery's readout over a loop.
- **`SettleBenchmark`, morph meshes:** rebuilding the showcase's morph meshes, front and back layer, and
  the front layer without comets, on the recipe's canvas. The transit mesh's settle is the difference.
- **`SettleBenchmark`, comet track build:** working out the showcase front layer's comets, forward and
  reverse, on the recipe's canvas: the rest of a morph's start, which the morph meshes reuse from a cache.
  The morph meshes plus this make up the cost of a morph's first frame.
- **`SettleBenchmark`, playground loop, playground morph meshes and playground comet track build:** the
  same three for "Comet playground", per morph: frame to blooms, blooms to cross and cross to frame. The
  loop prints a start and an end row for each morph.
- **`LivingDelaunayBenchmark`:** that preset's phases per frame, still. It settles its mesh every frame.
- **`AlphaSettleBenchmark`:** the alpha settle per frame, every layer summed, in three steps: `reference`,
  the old sweep over every node and edge every round; `per-node constants`, the same with each node's
  constants computed once a frame; and `frontier rounds`, the field's own. On the showcase and playground
  loops, `baseline` at density 20, 48 and 96, and living Delaunay, the stills over 120 frames.
- **`AlphaSettleReport`, showcase loop and playground loop:** the alpha settle's work per layer, still and
  morph frames apart: rounds, round cap hits, frontier nodes, relevant nodes and edges above alpha 0.
- **`StillFrameBenchmark`:** the shape and alpha settle phases and the whole `advance` per frame, every
  layer summed, with the still-frame cache off and on; the two sides' loops are interleaved, each side
  first every other loop. On the showcase's and the playground's start held still, the showcase held at
  fraction 0.5, `baseline` at density 20, 48 and 96, living Delaunay, and both loops, the stills over 120
  frames. With the cache on, it also prints the share of the pool outside the settle list and the share
  of layer frames that kept their presences, both over the first measured loop.

Each run starts with a header line naming the benchmark, the platform and the timer's measured step.
Without `MORPHEUS_BENCHMARK`, each benchmark class prints `<Class> skipped: MORPHEUS_BENCHMARK not set`
once instead, so a skip line means the flag didn't reach that platform. In headless Chrome the Karma
page is cross-origin isolated, so `performance.now()` steps at about 5 µs rather than 100 µs.

Use them next to the recipe, not instead of it. They cover the `advance` phases only: they don't draw,
so frame time and the fill, edges, halos, dots and composite phases still come from the recipe.

### Running one by hand

```sh
MORPHEUS_BENCHMARK=1 ./gradlew :morpheus:jvmTest --tests '*LivingDelaunayBenchmark*' :morpheus:wasmJsBrowserTest --tests '*LivingDelaunayBenchmark*' -i
```

`--tests` applies to the task named just before it, hence twice. On wasm, the filter needs the trailing
`*`. With `MORPHEUS_BENCHMARK` set, the test tasks always run, never up to date or from the cache.
Without it, or with it empty, the benchmarks return at once on both platforms after the skip line, so
CI never runs them. With it set, the wasm run also logs `MORPHEUS_BENCHMARK set: the benchmarks run in
the page`, so a wasm run that logs that and then a skip line shows the page didn't get the flag.
A filter that matches more than one benchmark runs them in one JVM or page, and every benchmark after
the first prints a "not run alone" warning: don't compare those numbers.

### Why each test runs alone

The JIT keeps what earlier code taught it. In the same Karma page, V8 inlines calls it has seen go one
way, so a benchmark's wasm timings depend on which tests ran before it. For example, the showcase loop's
edge fades measured about 9.6 ms a frame right after the morph-mesh test, and about 17 to 18 ms alone,
close to the gallery's 18.8 ms. The JVM showed no such effect, but runs alone too, the same way.

When a run reports the test more than once, the script prints the end summary and stops all runs. That
happens when a headless Chrome left over from a killed run is still open: it reconnects to Karma's port
and runs the test at the same time as the new one, which slows both down. Quit the leftover `Google Chrome for Testing` process,
whose `--user-data-dir` ends in `karma-…`.

### Writing a benchmark

A benchmark is a test in `morpheus/src/commonTest` whose body runs through
`benchmark(this::class, "case") { … }`, written as ``fun `what it times`() = benchmark(…)``: that is the
form the script finds. The class names the header and the skip line; the case, when a class has more
than one test, tells its headers apart. The harness, in
`BenchmarkHarness.kt`, gives it:

- `timeCalls`, for a call too short for the timer: it batches calls until a batch lasts at least 2 ms.
- `GalleryLoop`, which replays frames as the gallery's modifier runs them: one field per mesh, kept
  across frames as the modifier keeps it, each frame's phases timed through the gallery's own probe. By
  default it draws on the recipe's canvas at 60 frames a second. The gallery gives a sample a window of
  its canvas's size, so the 1280 px canvas also sets the grid's cell size.
- `meanFrame`, `medianPhases`, `meanPhases` and `millis`, so every benchmark averages and prints the same
  way on both platforms.

Print, don't assert: a benchmark reports and never fails a build.

## Comparing a change

Numbers only compare when they come from the same tier, the same benchmark and the same setup: specs,
canvas, device scale, machine, and JDK or browser. Numbers from older reports, other machines or other
specs don't compare with a new run; measure the base again instead.

1. Check the base, the merge-base of the change, out into a git worktree next to the change's checkout:

   ```sh
   git worktree add ../morpheus-base "$(git merge-base origin/develop HEAD)"
   ```

   A change stacked on another pull request compares against the head of the branch it's stacked on,
   not against `develop`:

   ```sh
   git worktree add ../morpheus-base origin/<parent-branch>
   ```

   If the change edits a spec or the harness in a way the base should share, copy that edit into the
   base worktree uncommitted, so both sides run the same thing.

2. Alternate between the two checkouts, at least three rounds each, on the same machine with nothing
   else heavy running: base, change, base, change and so on. Alternating spreads the machine's drift
   over both sides. Each round, run the same command in both:

   ```sh
   python3 scripts/headless_benchmark.py              # the headless tier
   python3 scripts/gallery_benchmark.py --loops 3     # the recipe in the browser
   ```

   Each run already gives medians: the headless benchmarks over their loops or runs, the recipe over
   its three loops.
3. Report each cell as the range of the rounds' medians, low-high, as PR #40 did. Within one checkout,
   `scripts/headless_benchmark.py --rounds N` prints that range for you; it can't alternate checkouts,
   so use it to check a side's spread, and take a comparison's cells from the alternated rounds.
4. Judge the change by those ranges:
   - **faster** means the change's slowest round median beats the base's fastest round median, for the
     phase the change targets;
   - **not worse** means, for every other phase, the two ranges overlap or the change is better;
   - anything else is a regression: report it and explain it in the pull request.

   A drawing change applies the same rules to the recipe's frame and phase columns.
5. When you're done, remove the worktree with `git worktree remove ../morpheus-base`.

Report one table per tier and platform, base and change side by side, with the machine, the OS, and
the JDK or Chrome for Testing version.

### Worked example

[PR #48](https://github.com/rmakowiecki/Morpheus/pull/48) made comets travel between clusters of the
real shapes instead of stand-in ellipses, planning their heads once at the morph's start. It changes the
CPU phases, so it gates on the headless numbers in Chrome and reports the recipe's wasm frame once.

- **Base:** it was stacked on PR #46, so the base was #46's head, `75bf5e8`, in a worktree. The change
  also widened the showcase's ribbons to 0.25; the base worktree took that edit uncommitted, so both
  sides timed the same specs.
- **Rounds:** headless, four rounds on the base and three on the change in Chrome, after one run hung;
  the recipe, five rounds on the base and four on the change. Base and change alternated throughout.
- **Setup:** Apple M1, macOS, Chrome for Testing 154.

Headless, Chrome, ranges of round medians:

| Benchmark                                     | Base             | Change           | Verdict    |
|-----------------------------------------------|------------------|------------------|------------|
| Comet track build, forward                    | 51.84–52.17 ms   | 4.12–4.36 ms     | faster     |
| Comet track build, way back                   | 52.48–52.91 ms   | 4.10–4.16 ms     | faster     |
| Morph meshes, two ends and transit            | 98.14–99.17 ms   | 94.13–95.03 ms   | not worse  |
| Loop, per frame: shape                        | 4.70 ms          | 4.78 ms          | regression |
| Loop, per frame: edge fades                   | 16.93 ms         | 13.51 ms         | not worse  |

The comet track build is the phase the change targets. The change's slowest round median, 4.36 ms,
beats the base's fastest, 51.84 ms, so it is **faster**. The morph meshes improve, so they are **not
worse**.

#48 reported each loop row as one median per side, so each is a range of one value and the same rules
apply. Edge fades improve, so they are **not worse**. Shape is 0.08 ms (2%) slower and the values don't
overlap, so it is a **regression**. #48 didn't measure its cause and accepted it along with the recipe's
regression below. The JVM numbers came from the same runs and are reported in the pull request, not
gated.

The recipe on wasm, ranges of round medians, ms per frame:

| Phase      | Base        | Change      | Verdict    |
|------------|-------------|-------------|------------|
| Frame      | 40.33–41.57 | 41.99–43.95 | regression |
| Edge fades | 23.02–23.49 | 24.91–25.95 | regression |

The ranges don't overlap and the change is slower, so by the rules above this is a regression, which the
pull request has to explain. It ran two more rounds on each side with the change set to the base's
comet settings: there the change was 14–17% faster than the base. So the cost came from the showcase's
tuned comets, which light more nodes and fade more edges at once, not from the new comet code, and it
was accepted as the chosen look.
