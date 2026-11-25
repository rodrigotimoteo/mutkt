# parity-legacy

Generates `mutkt-asm/src/test/resources/parity/legacy-0.3.3.tsv`: every site the legacy MutKt 0.3.3 operators find in the parity fixtures.
The rebuild compares itself with this file (sites only: operator, class, method+descriptor, line, opcode; ids and ordinals differ by design).

This is a standalone Gradle build, not part of the root build or its lint. The TSV is committed; rerun only when a fixture changes.

## Run

```bash
./gradlew -p tools/parity-legacy run --args="$PWD/mutkt-asm/src/test/resources/parity"
```

It resolves `io.github.rodrigotimoteo:mutation-core:0.3.3` from Maven Central; the jar's SHA-1 is written to the TSV header.
Without network, put the jar, pom and module in a Maven-layout directory and add it with an init script (`-I`) that declares it in `dependencyResolutionManagement`.

Each `*.kt.txt` fixture is compiled on its own the way `mutkt-testing`' `compileKotlin` does (file `Source.kt`, jvmTarget 17, Kotlin and kctfork versions from `gradle/libs.versions.toml`).
Fixtures name their facade with `@file:JvmName` so class names are unique across fixtures.
Then each of the 16 legacy operators runs alone through `Mutator(setOf(op)).scanMutations`. Output is sorted; two runs are byte-identical.

The header also holds the SHA-256 of every compiled fixture class and the legacy operator to new operator id map.
Opcode is the legacy `originalOpcode` (for TRUE_RETURNS and FALSE_RETURNS that is `ICONST_1`/`ICONST_0`, not the return).

Rows with line -1 come from legacy operators that record no line number. The TSV contains duplicate rows (identical sites): compare as a multiset.
