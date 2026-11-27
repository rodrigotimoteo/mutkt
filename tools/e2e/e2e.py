"""JSON side of tools/e2e/run.sh: reads the corpus and checks a Stryker report against its expectations.

Run with python3 -I. Subcommands:
  fields <corpus> [name]        one tab-separated line per repo: name url commit module jdk apply patch moduleDir
                                ("-" for null; moduleDir defaults to the module path with ":" read as "/")
  entry <corpus> <name>         the repo's corpus entry as JSON, for the init script
  gradle-args <corpus> <name>   the repo's gradleArgs, one per line (none when absent)
  check <expectations> <report> one summary line; exits 1 and prints the broken expectations when any fails
  compare <old> <new>           per mutant id, how a report's statuses changed; exits 1 when any changed other than
                                Survived to NoCoverage
  operators <reports dir> [--sample N]
                                per operator: mutants by status and share of all mutants in the directory's *.json
                                reports; --sample N adds N random Survived mutants per operator with their source line
"""
import glob
import json
import os
import random
import re
import sys
from collections import Counter

FIELDS = ["name", "url", "commit", "module", "jdk", "apply", "patch"]


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def repos(corpus_path, name=None):
    entries = load(corpus_path)["repos"]
    if name is None:
        return entries
    found = [e for e in entries if e["name"] == name]
    if not found:
        sys.exit(f"no repo named {name} in {corpus_path}; names: {', '.join(e['name'] for e in entries)}")
    return found


def gradle_args(entry):
    args = entry.get("gradleArgs") or []
    if not all(isinstance(a, str) and a and "\n" not in a for a in args):
        sys.exit(f"{entry['name']}: gradleArgs must be non-empty single-line strings")
    return args


def glob_regex(pattern):
    """MutKt's class glob (Glob.kt): * stays in one segment, ** crosses dots, ? is one non-dot character."""
    return re.compile(
        ".*".join(
            "[^.]*".join("[^.]".join(re.escape(lit) for lit in part.split("?")) for part in seg.split("*"))
            for seg in pattern.split("**")
        )
    )


def class_name(mutant_id):
    # Mutant ids read operator:class#method(descriptor):ordinal.
    return mutant_id.split(":", 1)[1].split("#", 1)[0]


def score(statuses):
    scorable = len(statuses) - statuses.count("RuntimeError")
    detected = statuses.count("Killed") + statuses.count("Timeout")
    return detected / scorable if scorable else None


def file_problems(expected_files, files):
    wanted = {"killedAtLeast": "Killed", "survivedAtLeast": "Survived", "noCoverageAtLeast": "NoCoverage"}
    for expected in expected_files:
        path = expected["path"]
        # Report keys are package directory plus file name; expectations give the path in the repo.
        keys = [k for k in files if path == k or path.endswith("/" + k)]
        if not keys:
            yield f"{path}: no mutants in the report"
            continue
        statuses = [m["status"] for m in files[keys[0]]["mutants"]]
        for field, status in wanted.items():
            least = expected.get(field, 0)
            if statuses.count(status) < least:
                yield f"{path}: {statuses.count(status)} {status}, expected at least {least}"


def problems(expect, report):
    files = report["files"]
    mutants = [m for f in files.values() for m in f["mutants"]]
    statuses = [m["status"] for m in mutants]
    count, low, high = len(mutants), expect["mutants"]["min"], expect["mutants"]["max"]
    if not low <= count <= high:
        yield f"{count} mutants, expected {low}..{high}"
    value, low, high = score(statuses), expect["score"]["min"], expect["score"]["max"]
    if value is None or not low <= value <= high:
        yield f"score {'none' if value is None else f'{value:.3f}'}, expected {low}..{high}"
    errors = statuses.count("RuntimeError")
    if errors > expect["maxError"]:
        yield f"{errors} ERROR mutants, expected at most {expect['maxError']}"
    seen = {m["mutatorName"] for m in mutants}
    for operator in expect.get("operators", []):
        if operator not in seen:
            yield f"no {operator} mutant"
    classes = {class_name(m["id"]) for m in mutants}
    for pattern in expect.get("mustNotContain", []):
        hits = sorted(c for c in classes if glob_regex(pattern).fullmatch(c))
        if hits:
            yield f"{pattern} matches mutated classes: {', '.join(hits[:3])}"
    yield from file_problems(expect.get("files", []), files)


def summary(report):
    statuses = [m["status"] for f in report["files"].values() for m in f["mutants"]]
    value = score(statuses)
    shown = "none" if value is None else f"{value:.1%}"
    return f"mutants={len(statuses)} score={shown} errors={statuses.count('RuntimeError')}"


def mutants(report):
    return {m["id"]: m for f in report["files"].values() for m in f["mutants"]}


def comparison(old, new):
    """Same status and Survived to NoCoverage are counted; every other change is listed with the tests involved."""
    before, after = mutants(old), mutants(new)
    pairs = [(before[i]["status"], after[i]["status"]) for i in before.keys() & after.keys()]
    whole_suite = sum("coveredBy" not in m for m in after.values())
    yield (
        f"same={sum(a == b for a, b in pairs)} survived->nocoverage={pairs.count(('Survived', 'NoCoverage'))}"
        f" whole-suite={whole_suite}/{len(after)} only-old={len(before.keys() - after.keys())}"
        f" only-new={len(after.keys() - before.keys())}"
    )
    for i in sorted(before.keys() & after.keys()):
        was, now = before[i]["status"], after[i]["status"]
        if was != now and (was, now) != ("Survived", "NoCoverage"):
            covered = ",".join(after[i].get("coveredBy", ["<whole suite>"])) or "<none>"
            killed = ",".join(before[i].get("killedBy", [])) or "-"
            yield f"  {was}->{now} {i} coveredBy={covered} oldKilledBy={killed}"


COLUMNS = ["Killed", "Survived", "NoCoverage", "Timeout", "RuntimeError"]


def mutant_rows(report_dir):
    for path in sorted(glob.glob(os.path.join(report_dir, "*.json"))):
        report = os.path.splitext(os.path.basename(path))[0]
        for key, file in load(path)["files"].items():
            for mutant in file["mutants"]:
                yield report, key, file.get("source", ""), mutant


def table_row(label, width, counts, total):
    n = sum(counts.values())
    cells = "  ".join(f"{counts[s]:>{len(s)}}" for s in COLUMNS)
    return f"{label:<{width}}  {n:>7}  {cells}  {n / total:>7.1%}"


def sample_lines(rows, names, sample):
    survived = {}
    for report, key, source, mutant in rows:
        if mutant["status"] == "Survived":
            survived.setdefault(mutant["mutatorName"], []).append((report, key, source, mutant))
    for name in names:
        pool = survived.get(name, [])
        yield f"{name}: {min(sample, len(pool))} random of {len(pool)} survivors"
        for report, key, source, mutant in random.sample(pool, min(sample, len(pool))):
            line = mutant["location"]["start"]["line"]
            lines = source.splitlines()
            text = lines[line - 1].strip() if line <= len(lines) else ""
            yield f"  {report} {key}:{line}  {mutant['description']}"
            yield f"      {text}"


def operators(report_dir, sample):
    rows = list(mutant_rows(report_dir))
    if not rows:
        sys.exit(f"no *.json mutation reports in {report_dir}")
    counts = {}
    for _, _, _, mutant in rows:
        counts.setdefault(mutant["mutatorName"], Counter())[mutant["status"]] += 1
    names = sorted(counts, key=lambda n: (-sum(counts[n].values()), n))
    width = max(len("operator"), len("all"), *(len(n) for n in names))
    total = len(rows)
    header = f"{'operator':<{width}}  {'mutants':>7}  " + "  ".join(f"{s:>{len(s)}}" for s in COLUMNS)
    print(header + f"  {'share':>7}")
    for name in names:
        print(table_row(name, width, counts[name], total))
    print(table_row("all", width, Counter(m["status"] for _, _, _, m in rows), total))
    if sample:
        print()
        print("\n".join(sample_lines(rows, names, sample)))


def sample_size(options):
    if not options:
        return 0
    if len(options) == 2 and options[0] == "--sample" and options[1].isdigit():
        return int(options[1])
    sys.exit("operators takes [--sample N] after the reports dir")


def main(args):
    command = args[0] if args else ""
    if command == "fields" and len(args) in (2, 3):
        for entry in repos(*args[1:]):
            module_dir = entry.get("moduleDir") or entry["module"].strip(":").replace(":", "/")
            print("\t".join(["-" if entry.get(f) is None else str(entry[f]) for f in FIELDS] + [module_dir]))
    elif command == "entry" and len(args) == 3:
        print(json.dumps(repos(args[1], args[2])[0]))
    elif command == "gradle-args" and len(args) == 3:
        for arg in gradle_args(repos(args[1], args[2])[0]):
            print(arg)
    elif command == "check" and len(args) == 3:
        try:
            report = load(args[2])
        except FileNotFoundError:
            sys.exit(f"no report at {args[2]}: set moduleDir in the corpus when the module is not at its path")
        found = list(problems(load(args[1]), report))
        print(summary(report))
        for problem in found:
            print(f"  {problem}")
        sys.exit(1 if found else 0)
    elif command == "compare" and len(args) == 3:
        lines = list(comparison(load(args[1]), load(args[2])))
        print("\n".join(lines))
        sys.exit(1 if len(lines) > 1 else 0)
    elif command == "operators" and len(args) in (2, 4):
        operators(args[1], sample_size(args[2:]))
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
