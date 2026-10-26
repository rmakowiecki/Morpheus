#!/usr/bin/env python3
"""Runs docs/benchmark.md's recipe in the browser with no one at the screen.

Builds the gallery's production bundle, serves it, and opens it in a separate Chrome with the recipe's
flags and `?benchmark`, which opens "Pills to ribbons" on the 16:9 preview with scale to fit off and prints
each half-second reading to the console. Chrome writes the console to stderr; this collects the readings
over whole loops of the showcase and prints, per loop and as the median of the loops, the frame, graphs
and phase-table totals in milliseconds per frame.

A Chrome window opens for the run. Leave it in front and the machine otherwise idle.

    python3 scripts/gallery_benchmark.py [--chrome PATH] [--loops 3] [--skip-build]
"""

import argparse
import functools
import http.server
import os
import pathlib
import shutil
import statistics
import subprocess
import sys
import tempfile
import threading
import time

ROOT = pathlib.Path(__file__).resolve().parent.parent
BUNDLE = ROOT / "samples/gallery/build/dist/wasmJs/productionExecutable"
PREFIX = "morpheus-benchmark "
LOOP_SECONDS = 10.0
WARM_UP_SECONDS = 6.0
COLUMNS = ["frame", "graphs", "shape", "mesh", "edge-fades", "alpha-settle", "fill", "edges", "halos", "dots", "composite"]
FLAGS = ["capped", "scaled", "switches"]
MAC_CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"


def chrome_flags(profile, url):
    """The recipe's flags (no frame-rate cap, device scale 2), a window wide enough for the recipe, and no throttling of a window that loses focus."""
    return [
        f"--user-data-dir={profile}",
        "--no-first-run",
        "--no-default-browser-check",
        "--disable-gpu-vsync",
        "--disable-frame-rate-limit",
        "--force-device-scale-factor=2",
        "--window-size=1440,900",
        "--disable-backgrounding-occluded-windows",
        "--disable-renderer-backgrounding",
        "--disable-background-timer-throttling",
        "--enable-logging=stderr",
        "--v=0",
        url,
    ]


def parse(line):
    """A reading's fields from a Chrome log line, or None for any other line."""
    start = line.find(PREFIX)
    if start < 0:
        return None
    reading = {}
    for field in line[start + len(PREFIX):].split('"')[0].split():
        key, _, value = field.partition("=")
        reading[key] = value == "true" if key in FLAGS else float(value)
    return reading


def serve(directory, port):
    """Serves [directory] on 127.0.0.1:[port] from a background thread; returns the server, to shut down."""
    handler = functools.partial(QuietHandler, directory=str(directory))
    server = http.server.ThreadingHTTPServer(("127.0.0.1", port), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


class QuietHandler(http.server.SimpleHTTPRequestHandler):
    """Serves the bundle without a log line per request, and the wasm module with the type browsers require."""

    extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map, ".wasm": "application/wasm"}

    def log_message(self, format, *args):
        pass


def collect(chrome, url, loops):
    """Each reading after the warm-up, with its arrival time from the first, over [loops] loops."""
    profile = tempfile.mkdtemp(prefix="morpheus-benchmark-")
    process = subprocess.Popen([chrome, *chrome_flags(profile, url)], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True, errors="replace")
    readings = []
    first = None
    deadline = None
    try:
        for line in process.stderr:
            reading = parse(line)
            if reading is None:
                continue
            now = time.monotonic()
            if first is None:
                first = now
                print(f"first reading; warming up for {WARM_UP_SECONDS:.0f} s, then {loops} loops of {LOOP_SECONDS:.0f} s", file=sys.stderr)
            if now - first < WARM_UP_SECONDS:
                continue
            if deadline is None:
                deadline = now + loops * LOOP_SECONDS
            if now >= deadline:
                break
            readings.append((now - first - WARM_UP_SECONDS, reading))
    finally:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
        shutil.rmtree(profile, ignore_errors=True)
    return readings


def report(readings, loops):
    """Warns about readings that aren't benchmark numbers, then prints each column's mean per loop and the median of the loops."""
    flagged = [reading for _, reading in readings if any(reading[flag] for flag in FLAGS)]
    if flagged:
        names = sorted({flag for reading in flagged for flag in FLAGS if reading[flag]})
        print(f"warning: {len(flagged)} of {len(readings)} readings flagged {', '.join(names)}; they are not benchmark numbers", file=sys.stderr)
    backgrounds = sorted({int(reading["backgrounds"]) for _, reading in readings})
    if backgrounds != [1]:
        print(f"note: backgrounds per reading {backgrounds}; the phases sum every background", file=sys.stderr)
    per_loop = []
    for loop in range(loops):
        window = [reading for arrival, reading in readings if loop * LOOP_SECONDS <= arrival < (loop + 1) * LOOP_SECONDS]
        if window:
            per_loop.append((len(window), {column: statistics.fmean(reading[column] for reading in window) for column in COLUMNS}))
    width = max(len(column) for column in COLUMNS) + 2
    print("ms per frame".ljust(14) + "".join(column.rjust(width) for column in COLUMNS))
    for index, (count, means) in enumerate(per_loop):
        print(f"loop {index + 1} ({count:2d})".ljust(14) + "".join(f"{means[column]:.2f}".rjust(width) for column in COLUMNS))
    counts = ["nodes", "visible-nodes", "drawn-edges"]
    print("counts per frame, mean: " + ", ".join(f"{count} {statistics.fmean(reading[count] for _, reading in readings):.0f}" for count in counts), file=sys.stderr)
    if per_loop:
        print("median".ljust(14) + "".join(f"{statistics.median(loop_means[column] for _, loop_means in per_loop):.2f}".rjust(width) for column in COLUMNS))


def main():
    """Builds unless told not to, serves the bundle, collects the readings and reports them."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--chrome", default=os.environ.get("CHROME_BIN", MAC_CHROME), help="Chrome or Chrome for Testing binary (default: $CHROME_BIN, else Google Chrome)")
    parser.add_argument("--loops", type=int, default=3, help="whole 10 s loops of the showcase to measure (default: 3)")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--skip-build", action="store_true", help="serve the bundle as last built")
    args = parser.parse_args()
    if not args.skip_build:
        subprocess.run([str(ROOT / "gradlew"), ":samples:gallery:wasmJsBrowserDistribution", "-q"], cwd=ROOT, check=True)
    version = subprocess.run([args.chrome, "--version"], capture_output=True, text=True).stdout.strip()
    print(f"browser: {version}", file=sys.stderr)
    server = serve(BUNDLE, args.port)
    try:
        readings = collect(args.chrome, f"http://127.0.0.1:{args.port}/?benchmark", args.loops)
    finally:
        server.shutdown()
    if not readings:
        sys.exit("no readings: the gallery printed none (is the window in front, and does the bundle have benchmark mode?)")
    report(readings, args.loops)


if __name__ == "__main__":
    main()
