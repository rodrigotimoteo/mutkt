"""JSON side of tools/e2e/run.sh: reads the corpus and checks a Stryker report against its expectations.

Run with python3 -I. Subcommands:
  fields <corpus> [name]        one tab-separated line per repo: name url commit module jdk apply patch moduleDir
                                ("-" for null; moduleDir defaults to the module path with ":" read as "/")
  entry <corpus> <name>         the repo's corpus entry as JSON, for the init script
  gradle-args <corpus> <name>   the repo's gradleArgs, one per line (none when absent)
  check <expectations> <report> one summary line; exits 1 and prints the broken expectations when any fails
  compare <old> <new>           per mutant id, how a report's statuses changed; exits 1 when any changed other than
                                Survived to NoCoverage
"""
import json
import re
import sys

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
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
