---
name: mutkt-testing
description: Use when writing or reviewing tests in MutKt: conventions, shared fixtures, real compiled Kotlin bytecode, contract tests, fakes for ports, TestKit for the plugin.
---

# MutKt testing conventions

## Shape
- JUnit 5, test files ≤ 400 lines, one subject per file. Names in backticks describing behavior.
- Domain and application tests use **fakes** of the ports (in shared fixtures), not mocks. MockK only at adapter edges when a fake is impractical.
- Deterministic: no sleeps for synchronization, no wall-clock assertions beyond generous timeouts, no ordering dependence on parallel streams.
- No global state; if a test needs reset, the code under test is wrong (no singletons).

## Bytecode tests
- Build class bytes **only** through the shared fixtures: `compileKotlin("""…""")` for real compiler output and `ClassBuilder` for shapes Kotlin cannot produce. Never copy ASM boilerplate into a test file.
- Every operator runs through the **parametrized contract test**: each discovered site applies, mutated bytes differ from the original, `CheckClassAdapter` verification passes, and invoking the method shows changed behavior where applicable.
- Cover edge shapes: long/double (two slots), static vs instance, overloads (same name, different descriptor), nested/inner classes, lambdas, methods declared before/after related methods.
- Assert on the mutant list (ids, lines, descriptions) and on behavior of the loaded mutant.

## Engine and executor tests
- Real class loader and real JUnit Launcher on tiny fixture tests; include a deliberately weak test (mutant must survive) and an infinite-loop mutant (TIMEOUT).
- Every mutant ends with exactly one status; assert no mutant is silently dropped.

## Gradle plugin tests
- Only in `mutkt-gradle`, via Gradle TestKit with temp projects; run on the minimum and latest supported Gradle; configuration cache on; each scenario < 60 s. Android scenarios need real AGP (P5).

## Parity and golden tests
- Golden reports are plain text/JSON files next to the test; update them deliberately and explain why in the PR.

## Commands
```bash
./gradlew :mutkt-asm:test --tests '*Operator*'
./gradlew koverVerify      # 85% line per published module
```
