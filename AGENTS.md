# MutKt (rebuild)

Mutation testing for Kotlin/JVM: a Gradle plugin that mutates compiled bytecode and runs the project's existing JUnit tests against each mutant. Zero test changes required.

This branch (`rebuild/v1`) is a from-zero rebuild. The old code lives on `master` and tag `v0.3.3`: use it only as a behavior reference (`git worktree add ../mutkt-legacy v0.3.3`), never copy code from it.

Work comes from Linear: team ROD, project **MutKt Rebuild**. Read the issue, its blockers, and the project documents (architecture, legacy inventory, gap register) before starting.

## Principles

1. **Clean architecture.** Dependencies point inward: `domain` ← `application` ← adapters (`asm`, `junit`, `report`, `gradle`).
2. **YAGNI.** No feature, option, port or abstraction without an open task that needs it. Deferred features stay in Linear (P6/P7).
3. **Short code.** Main files ≤ 300 lines, test files ≤ 400, one operator per file, functions ≤ 40 lines.
4. **Correct before fast.** No performance work until the 1.0 RC (ROD-135).
5. **Real inputs in tests.** Bytecode tests run on real compiler output, not hand-made ASM only.

## Code style (low verbosity)

- Say it once: no restating comments, no KDoc on private code, one-line KDoc on public API only.
- Comments explain *why* (a bytecode quirk, a decision). Never narrate history, tickets or "previously".
- Prefer small immutable data classes, expression bodies, `when`, sealed types; no helper classes for one call site.
- No config knob, flag, interface or module "for later". Delete dead code in the same change.
- Errors: fail fast with one clear message; no catch-and-log-and-continue.
- Linear holds tasks and decisions only; code facts live in code and `docs/`, not in issue prose.

## Modules

| Module | Role | May depend on |
|---|---|---|
| `mutkt-annotations` | `@SuppressMutations` only | nothing |
| `mutkt-core` | `domain/` (Mutant, Status, Report, Score) and `application/` (use cases, ports, config) | nothing outside Kotlin stdlib |
| `mutkt-asm` | Operator framework, operators, `MutantGenerator` adapter | core, annotations, ASM |
| `mutkt-junit` | Isolated class loader, JUnit Platform `TestExecutor` | core, JUnit Platform Launcher |
| `mutkt-report` | Console, HTML, JSON writers | core |
| `mutkt-gradle` | Plugin, DSL, task, worker, wiring | everything above |
| `mutkt-testing` | Unpublished test support: `ClassBuilder`, `compileKotlin`, port fakes | core |

Adapters never depend on each other. Only `mutkt-gradle` wires them together. Architecture tests (ROD-77) enforce this.

Ports (application layer): `ClassSource`, `MutantGenerator`, `TestExecutor`, `ReportWriter`, `SourceLookup`. Add a port only when a task needs one.

## Hard rules

- No `object` singletons with mutable state; no global registries.
- No `println`/`System.err` in main code.
- Domain classes hold no bytecode and no framework types.
- Mutant ids include the method descriptor.
- One operator definition drives both discovery and application (`sites()` then `apply()`); never duplicate predicates.
- Operators never reference other operators.
- Versions only in `gradle/libs.versions.toml`.
- Public API of published modules is explicit (`explicitApi()`).

## Provisional decisions (change in Linear before they bite)

Defaults until the decision issues close: package `io.github.rodrigotimoteo.mutkt` (ROD-81); statuses `KILLED, SURVIVED, TIMEOUT, NO_COVERAGE, ERROR`, score = (KILLED + TIMEOUT) / (total − ERROR) (ROD-82); reports console + HTML + Stryker-schema JSON (ROD-83); no JUnit extension API/runner module (ROD-84); JDK 17 and 21, Jupiter engine by default (ROD-85).

## Commands

```bash
./gradlew build                  # compile + all checks + tests
./gradlew :mutkt-core:test       # one module
./gradlew ktlintCheck detekt     # lint (CI order: ktlint, detekt, test, build)
./gradlew ktlintFormat           # auto-fix style
./gradlew koverVerify            # coverage gate (85% line per published module)
```

Inline comments inside argument lists break ktlint: put comments on their own line.

## Workflow (humans and agents)

1. Pick an unblocked issue in the lowest open milestone. Check its label: `agent:worker`, `agent:implementer` or `agent:deep`.
2. Write the failing test first, then the smallest change that passes. Use skill `mutkt-dev`; for operators also `mutkt-operator`; for tests `mutkt-testing`.
3. Run module checks, then the full `./gradlew build` before reporting.
4. Run the `mutkt-reviewer` agent on every non-trivial diff. Fix findings or record why not.
5. Update the Linear issue (status + one comment: what changed, what was verified, what was not).
6. Commit and push as you go: small commits that each build and pass tests, several per issue is fine. Messages explain why and reference the issue (`ROD-n`). Push after each meaningful commit.

### Branches, worktrees, PRs

- `rebuild/v1` is the integration branch. Each issue works on its own branch off it, named with Linear's branch name (e.g. `rodrigotimoteo2603/rod-86-domain-model…`).
- One task at a time in this checkout. For a second concurrent task use a separate worktree: `git worktree add ../mutkt-rod-NN -b <branch> rebuild/v1`. Never run two tasks in the same working tree; remove the worktree after merge.
- Merge only through a PR into `rebuild/v1` (CI green, reviewer pass done). Squash is not required; keep the useful commits.
- Subagents write code but do not commit or push; the main session reviews the diff, re-runs checks, then commits and pushes. Never tag, publish, force-push, or touch secrets without the owner.

Subagent briefs follow `docs/agent-brief-template.md`. Agent output is a claim: re-run the checks and read the diff.

## Definition of done

Acceptance criteria in the issue met; tests added; `./gradlew build` green; no file over the size limits; architecture tests green; reviewer pass done; docs row/ADR updated when behavior or a decision changed.
