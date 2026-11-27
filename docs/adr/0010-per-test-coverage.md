# 0010 Per-test coverage selects each mutant's tests

## Context

Every selected test ran against every mutant, so a mutant cost a whole suite run and NO_COVERAGE (ADR 0002) only meant that no test was selected at all (`includeTags` or `engines` select none). ROD-237 decided to record per-test-class coverage in the baseline and run each mutant against the test classes that reach it (ROD-238 instruments the sites, ROD-239 records the hits per test class, ROD-240 uses them).

## Decision

The baseline runs on classes with a probe before each mutation site. A mutant runs only the test classes whose baseline run hit its probe, in baseline order, so an order-dependent suite sees them as in a full run and `killedBy` is deterministic; running the fastest first is left to ROD-135. Its deadline is 4 times the sum of their baseline time and a warm-up allowance, the time of the baseline's longest test class, plus 4 s, at most `timeoutMs`. Every run restarts shared set-up, such as a Spring `ApplicationContext`, in whichever class runs first, and does so under parallel load, so the allowance is scaled like the tests' own time. The baseline paid that set-up once, in the class that first needed it, and the cached context makes later Spring classes look cheap, so neither the covering classes' times nor the first class's time bound it; on spring-petclinic-kotlin, adding the allowance unscaled left 49 of 64 mutants in TIMEOUT, scaling it left 2.

NO_COVERAGE now also means: no test class reaches the mutated code, and the mutant is not run. Its place in the score is unchanged: it counts as not detected, Score = (KILLED + TIMEOUT) / (total - ERROR).

A mutant runs the whole suite, with the whole suite's deadline, when coverage cannot name its tests:

- its site has no probe (an interface, a method the probes would push over the JVM size limit);
- its probe was hit outside any test class (test discovery, engine start), which no test class owns;
- it is in a class initialiser, which runs once per run under whichever test class loads the class first;
- the baseline recorded no coverage;
- the instrumented baseline is red while the original classes pass; the run warns `The baseline on probed classes failed (<cause>); running every mutant against the whole suite` and goes on without coverage. If the original classes fail too, the run fails as before.

## Consequences

- Coverage says a test executed the code, not that it checked the result: a test that reaches a mutant but never checks its effect still yields SURVIVED.
- A subset runs its classes without the classes that do not cover the mutant, so an order-dependent suite (state one class leaves for the next) can give a different result than a full run.
- A hit from a thread that outlives its test class is credited to the next class that runs; since the probe registry is shared by the whole JVM, a stray thread left by an earlier run on a reused child credits its hits to the current run the same way. That can turn KILLED into SURVIVED (the class that would kill is not selected), never into NO_COVERAGE, since the hit is still recorded.
- Code that runs while a class initialises (methods called from `<clinit>`, object or companion `init`, top-level property initialisers) is attributed to the first test class that triggers it, so a later test class that depends on what it set is not selected. ROD-241 compares against full-suite runs; if that turns KILLED into SURVIVED, mutants in such methods run the whole suite too.
- `RunMutationAnalysis` takes a `CoverageInstrumenter`; `CoverageMap` (application) holds the rules above.
- A probed class fetches its probe array, on its first probe, from a process-wide registry in `mutkt-junit` (`ProbeRegistry`, keyed by class name) that it reaches through `ClassLoader.getSystemClassLoader()`. Robolectric's sandbox loader defines every project class again from its class file, and `java.*` is the only code all copies share, so this is how their hits land in one array. The registry is the one allowed piece of global mutable state; reading it never touches, loads or initializes a class.
