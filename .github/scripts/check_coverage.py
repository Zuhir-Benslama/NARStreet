#!/usr/bin/env python3
"""Fail CI if unit-test line coverage is below the configured threshold.

Usage: check_coverage.py <path-to-jacocoTestReport.xml> [min-line-coverage-percent]
"""

import sys
import xml.etree.ElementTree as ET


def main() -> int:
    if len(sys.argv) < 2:
        print(f"usage: {sys.argv[0]} <report.xml> [min-percent]", file=sys.stderr)
        return 2

    report_path = sys.argv[1]
    min_percent = float(sys.argv[2]) if len(sys.argv) > 2 else 30.0

    root = ET.parse(report_path).getroot()
    counters = [c for c in root.iter() if c.tag.split("}")[-1] == "counter"]

    covered = 0
    missed = 0
    for counter in counters:
        if counter.get("type") == "LINE":
            covered = int(counter.get("covered", 0))
            missed = int(counter.get("missed", 0))

    if covered + missed == 0:
        print(f"ERROR: no line coverage data found in {report_path}", file=sys.stderr)
        return 1

    percent = covered * 100.0 / (covered + missed)
    print(f"Line coverage: {percent:.1f}% ({covered} covered / {covered + missed} total)")

    if percent < min_percent:
        print(f"ERROR: line coverage {percent:.1f}% is below required {min_percent:.1f}%", file=sys.stderr)
        return 1

    print(f"OK: line coverage {percent:.1f}% >= {min_percent:.1f}%")
    return 0


if __name__ == "__main__":
    sys.exit(main())
