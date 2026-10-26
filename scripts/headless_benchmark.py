#!/usr/bin/env python3
"""Runs the headless benchmarks, each test alone, on the JVM and in headless Chrome.

Every benchmark test in morpheus's commonTest (a test whose body runs through `benchmark(...)`) runs in
its own Gradle test run, so each gets a fresh JVM or a fresh Karma page. On wasm, timings depend on what
ran earlier in the same page, since V8 inlines from the calls it has seen; run alone, the numbers don't.
Rounds interleave the tests; with more than one round, it prints each line's numbers as their range over
the rounds, low-high, the cell docs/benchmark.md asks a comparison to report.

    python3 scripts/headless_benchmark.py [--rounds 3] [--platform jvm|wasm|both] [--tests SUBSTRING]

On wasm, set CHROME_BIN to a Chrome or Chrome for Testing binary if Karma can't find one.
"""

import argparse
import os
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ElementTree

ROOT = pathlib.Path(__file__).resolve().parent.parent
TESTS = ROOT / "morpheus/src/commonTest/kotlin"
PACKAGE = "com.rossomak.morpheus"
TASKS = {"jvm": "jvmTest", "wasm": "wasmJsBrowserTest"}
CLASS = re.compile(r"^class (\w+)", re.MULTILINE)
BENCHMARK_TEST = re.compile(r"fun `([^`]+)`\(\) = benchmark\(")
NUMBER = re.compile(r"\d+(?:\.\d+)?")


def discover():
    """Each benchmark test as (class, test name), in file order: the class is the last one declared before the test."""
    tests = []
    for path in sorted(TESTS.rglob("*.kt")):
        source = path.read_text()
        classes = [(match.start(), match.group(1)) for match in CLASS.finditer(source)]
        for test in BENCHMARK_TEST.finditer(source):
            enclosing = [name for start, name in classes if start < test.start()][-1]
            tests.append((enclosing, test.group(1)))
    return tests


def run(platform, test_class, test):
    """Runs one test alone and returns the lines it printed."""
    task = TASKS[platform]
    results = ROOT / "morpheus/build/test-results" / task
    for old in results.glob("TEST-*.xml"):
        old.unlink()
    # The trailing * is what the wasm test filter needs to match a method; on the JVM it matches the same one.
    command = [str(ROOT / "gradlew"), f":morpheus:{task}", "--tests", f"{PACKAGE}.{test_class}.{test}*", "-q"]
    subprocess.run(command, cwd=ROOT, check=True, env={**os.environ, "MORPHEUS_BENCHMARK": "1"})
    lines = []
    test_cases = 0
    for result in results.glob("TEST-*.xml"):
        suite = ElementTree.parse(result).getroot()
        test_cases += len(suite.findall("testcase"))
        output = suite.findtext("system-out") or ""
        lines += [line.removeprefix("[log] ") for line in output.splitlines() if line.strip()]
    if test_cases != 1:
        # A headless Chrome left over from a killed run reconnects to Karma's port and runs the test as well, at the same time.
        sys.exit(
            f"{test_class}.{test} ran {test_cases} times on {platform}; on wasm, look for a leftover "
            "'Google Chrome for Testing --user-data-dir=.../karma-...' process and quit it",
        )
    if not lines:
        sys.exit(f"{test_class}.{test} printed nothing on {platform}")
    return lines


def ranges(rounds):
    """The lines of the first round with each number replaced by its range over the rounds, or None when the rounds printed different lines."""
    templates = [[NUMBER.sub("#", line) for line in lines] for lines in rounds]
    if any(template != templates[0] for template in templates):
        return None
    merged = []
    for index, line in enumerate(rounds[0]):
        columns = list(zip(*(NUMBER.findall(lines[index]) for lines in rounds)))
        values = iter(range_text(column) for column in columns)
        merged.append(NUMBER.sub(lambda _: next(values), line))
    return merged


def range_text(texts):
    """The range of numbers printed as [texts], as "low-high" with as many decimals as they were, or the number alone when every round printed the same."""
    numbers = [float(text) for text in texts]
    if min(numbers) == max(numbers):
        return texts[0]
    decimals = len(texts[0].partition(".")[2])
    return f"{min(numbers):.{decimals}f}-{max(numbers):.{decimals}f}"


def main():
    """Runs the matching benchmark tests, each alone, for every round and platform, and prints what they printed."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--rounds", type=positive_int, default=1, help="times to run every test, interleaved (default: 1)")
    parser.add_argument("--platform", choices=["jvm", "wasm", "both"], default="both")
    parser.add_argument("--tests", default="", help="only the tests whose class or name contains this")
    args = parser.parse_args()
    platforms = ["jvm", "wasm"] if args.platform == "both" else [args.platform]
    tests = [(test_class, test) for test_class, test in discover() if args.tests in f"{test_class}.{test}"]
    if not tests:
        sys.exit("no benchmark tests match")
    printed = {}
    for round_index in range(args.rounds):
        for test_class, test in tests:
            for platform in platforms:
                print(f"round {round_index + 1}/{args.rounds}: {test_class}.{test} on {platform}", file=sys.stderr)
                lines = run(platform, test_class, test)
                printed.setdefault((test_class, test, platform), []).append(lines)
                if args.rounds == 1:
                    print("\n".join(lines) + "\n")
    if args.rounds == 1:
        return
    for (test_class, test, platform), rounds in printed.items():
        merged = ranges(rounds)
        if merged is None:
            print(f"{test_class}.{test} on {platform} printed different lines per round; each round as printed:")
            merged = [line for lines in rounds for line in lines]
        else:
            print(f"range over {len(rounds)} rounds:")
        print("\n".join(merged) + "\n")


def positive_int(text):
    """[text] as a whole number of at least 1, for argparse; anything else is a usage error."""
    value = int(text)
    if value < 1:
        raise argparse.ArgumentTypeError(f"must be at least 1, not {value}")
    return value

if __name__ == "__main__":
    main()
