"""Writes docs/benchmark.md from a release-gate run: corpus.json, one Stryker report per repo and benchmark-run.json.

Run with python3 -I. Usage: python3 -I tools/e2e/benchmark.py <reports dir>
The reports dir holds <repo name>.json for every repo in corpus.json.
"""
import json
import sys
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
CORPUS = HERE / "corpus.json"
RUN = HERE / "benchmark-run.json"
OUT = HERE.parent.parent / "docs" / "benchmark.md"
HEAD = [
    "Repo",
    "Kind",
    "Test style",
    "Mutants",
    "Score",
    "Killed / Survived / NoCoverage / Timeout",
    "Wall time",
    "Machine",
]


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def row(entry, report, run):
    statuses = [m["status"] for f in report["files"].values() for m in f["mutants"]]
    counts = Counter(statuses)
    scorable = len(statuses) - counts["RuntimeError"]
    detected = counts["Killed"] + counts["Timeout"]
    score = f"{detected / scorable:.1%}" if scorable else "none"
    counted = [counts[s] for s in ("Killed", "Survived", "NoCoverage", "Timeout")]
    wall = run["wallTime"].get(entry["name"])
    if wall is None:
        sys.exit(f"{entry['name']}: no wallTime in {RUN}")
    return [
        entry["name"],
        entry["type"],
        ", ".join(entry["tests"]),
        str(len(statuses)),
        score,
        " / ".join(str(c) for c in counted),
        wall,
        run["machine"],
    ]


def page(rows, run):
    lines = [
        "# Benchmark",
        "",
        f"MutKt on {len(rows)} open-source repos from the end-to-end suite. Each repo is pinned to a commit and checked"
        f" against its expectations. Numbers are from the release-gate run of v{run['version']}"
        f" (commit `{run['commit']}`, {run['date']}).",
        "",
        "Score is (Killed + Timeout) / (mutants - ERROR), as in the console summary. NoCoverage mutants count against it.",
        "",
        "| " + " | ".join(HEAD) + " |",
        "|" + "---|" * len(HEAD),
    ]
    lines += ["| " + " | ".join(r) + " |" for r in rows]
    return "\n".join(lines) + "\n"


def main(args):
    if len(args) != 1:
        sys.exit(__doc__)
    reports = Path(args[0])
    run = load(RUN)
    rows = []
    for entry in load(CORPUS)["repos"]:
        path = reports / f"{entry['name']}.json"
        if not path.exists():
            sys.exit(f"no report for {entry['name']} at {path}")
        rows.append(row(entry, load(path), run))
    OUT.write_text(page(rows, run), encoding="utf-8")
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main(sys.argv[1:])
