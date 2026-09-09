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

    def local_name(element) -> str:
        return element.tag.split("}")[-1]

    def line_counter(element) -> tuple[int, int] | None:
        for child in element:
            if local_name(child) == "counter" and child.get("type") == "LINE":
                return int(child.get("covered", 0)), int(child.get("missed", 0))
        return None

    # Prefer the report-level summary counter (direct child of <report>). If absent,
    # fall back to summing each package's roll-up counter to avoid double-counting
    # class- or sourcefile-level lines.
    covered = missed = 0
    summary = line_counter(root)
    if summary is not None:
        covered, missed = summary
    else:
        for package in root:
            if local_name(package) != "package":
                continue
            if counter := line_counter(package):
                covered += counter[0]
                missed += counter[1]

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
