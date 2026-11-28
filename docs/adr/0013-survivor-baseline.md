# 0013 Survivor baseline

## Context

A project adopting MutKt has survivors it cannot kill at once, so `failBelowScore` either fails every build or is set low enough to let new survivors through. ROD-133 asks for a committed list of accepted survivors and a task flag that fails only on the others. A mutant id (`operator:class#method(descriptor):ordinal`) numbers the sites of one operator in a method, so an id alone does not name the same code across edits: a new site earlier in the method takes the ordinal of an accepted survivor.

## Decision

`mutktUpdateBaseline` runs the `mutkt` analysis and writes its SURVIVED and NO_COVERAGE mutants to `mutkt-baseline.json` in the project directory: `{"version": 1, "survivors": [{"id": "…", "method": "…"}]}`, sorted by id so the committed file diffs cleanly. With `failOnNewSurvivors`, `mutkt` fails when a survivor is not in it. NO_COVERAGE counts because it lowers the score like SURVIVED, and new untested code must not pass the gate.

**Identity policy.** An entry is the mutant id and `method`, the SHA-256 of its method's code as `mutkt-asm` reads it without debug information (line numbers, local variable names) and frames; the method is written alone into an empty class, so the digest depends on nothing else in the class. A survivor is accepted only when both match:

- Unchanged code, and an edit that only moves lines, keep the digest: the survivor is accepted.
- Any other change to the method's code changes the digest, so every survivor of that method is new, even when its id is in the baseline (the message marks it `accepted before its method changed: review it`). This covers a new site earlier in the method, whose survivor would otherwise inherit an accepted ordinal. It is deliberately coarse: an unrelated change in the same method also asks for a review, which is a rerun of `mutktUpdateBaseline`, never a missed survivor.
- A renamed method or class, or kotlinc renumbering lambdas, changes the id: those survivors are new.
- Bytecode that changes without a source edit of the method asks for a review too: a Kotlin or Java compiler upgrade, an ASM upgrade in MutKt, or an edit to an `inline` function that the method calls (its body is copied into the caller). The fix is to review and rerun `mutktUpdateBaseline`.

The digests are computed only when the gate is on or for the update, after the reports are written; a survivor whose method is missing from the compiled classes fails the task with its id. The baseline file is an input of `mutkt` only while `failOnNewSurvivors` is on. `mutktUpdateBaseline` ignores `failBelowScore`, since accepting the current survivors is its purpose; on `mutkt`, a failing score gate and a failing survivor gate are reported together.

The result history's keys (ADR 0011) are not reused: they hash the mutated class bytes, line numbers included, and the covering tests, so a line-only edit or a new test would drop accepted survivors. The file follows the history's shape (versioned JSON tree in `mutkt-core`, mutant id text as `id`), but a file of another version fails the task instead of being ignored, since ignoring a committed baseline would turn every survivor into a new one.

## Consequences

- Any code change in a method with accepted survivors fails the gate until the baseline is updated; the update is one task and its diff shows which ids changed.
- The update replaces the file with the survivors of its run, so a narrower `targetClasses` writes a narrower baseline.
- Both tasks write the same reports directory, which shows whichever ran last, and share `historyFile`, so the update right after `mutkt` reuses every result; `mutkt` is ordered after the update when both are requested.
- Status: accepted under owner autopilot, pending owner confirmation.
