#!/usr/bin/env python3
"""Print a Markdown table from one or more k6 --summary-export files (perf/results/*/summary.json)."""
import json
import sys

STATS = ["med", "p(95)", "p(99)", "max"]


def rows(path):
    metrics = json.load(open(path))["metrics"]
    reqs = metrics["http_reqs"]
    failed = metrics.get("http_req_failed", {})
    print(f"\n**{path}** — {reqs['count']} requests, {reqs['rate']:.1f} req/s, "
          f"error rate {100 * failed.get('value', 0):.2f}%\n")
    print("| metric | " + " | ".join(STATS) + " |")
    print("|---" * (len(STATS) + 1) + "|")
    for name, m in sorted(metrics.items()):
        if name == "http_req_duration" or "{name:" in name or name.endswith("_run_duration") or name.endswith("adoption_duration"):
            values = m.get("values", m)
            print(f"| {name} | " + " | ".join(f"{values[s]:.1f} ms" for s in STATS) + " |")


for p in sys.argv[1:]:
    rows(p)
