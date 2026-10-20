# Benchmark recipe

One way to measure the graph renderer's cost, on desktop and in the browser. Every pull request that
claims a performance change reports before and after numbers from this recipe, for the JVM and for
wasm, and links here rather than repeating the steps.

## What is measured

- **Sample:** "Pills to ribbons" (the hero) at its default specs. Don't tune it, or press Reset to
  defaults first.
- **Canvas:** the gallery's **16:9** preview, 640×360 dp, with **Scale to fit off**, at **device
  scale 2**: 1280×720 physical pixels. Browser zoom is at 100%.
- **No frame-rate cap:** with vsync on, the frame interval stops at the display's refresh interval
  once a frame costs less than that, and hides the cost. The flags below turn it off, so the frame
  interval is the real cost per frame.
- **Over a full loop:** the hero's loop is 10 s, pills to ribbons and back. Read the numbers as they
  settle over a loop, not from a single half-second window.

## The readout

The gallery's control row has a button reading `frame 9.8 ms · graphs 6.1 ms`, refreshed twice a
second. Clicking it opens the breakdown panel, in the side column, under the Tune sheet when that is
open (under the preview in a narrow window). The button and the panel dim from a reset, such as a sample change, until the
next reading arrives:

- **frame** is the average interval between frames; **graphs** is the CPU time spent recording the
  graphs into the display list, every graph on the canvas summed.
- The **phase table** splits the graphs' time per frame into shape, mesh, edge fades, alpha settle,
  fill, edges, halos, dots and composite, in total and per spec layer, back to front.
- The **counts** are per frame (nodes, visible nodes, drawn edges, halos and dots, draw calls) or per
  half-second window (settles, morph meshes, meshes).
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

### Then, on either platform

1. Open "Pills to ribbons". Press Reset to defaults if it was tuned.
2. Pick the **16:9** preview and turn **Scale to fit** off.
3. Open the breakdown panel by clicking the frame time button in the control row. It opens in the side
   column; open the Tune sheet as well if the numbers should cover tuning, and the two share the column.
   Neither takes canvas height or width from the other.
4. Check that the button says neither "capped" nor "canvas scaled".
5. With every switch off, watch a full loop and record frame, graphs and the phase table.
6. Turn on each skip switch in turn, alone, and record the same over a loop.

Changing the sample, theme or preview, editing a spec, pausing or resuming, or flipping a switch
starts a fresh window, so no reading mixes two setups.

## Reporting

Report one table per platform in the pull request:

| Setup              | frame ms | graphs ms | edges ms | halos ms | dots ms | fill ms |
|--------------------|----------|-----------|----------|----------|---------|---------|
| all switches off   |          |           |          |          |         |         |
| skip edges         |          |           |          |          |         |         |
| skip halos         |          |           |          |          |         |         |
| skip dots          |          |           |          |          |         |         |
| skip fill recolor  |          |           |          |          |         |         |

Add the machine, OS, and browser or JDK version, and the full phase table for the baseline row.
