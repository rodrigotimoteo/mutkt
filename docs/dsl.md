# DSL reference

```kotlin
plugins { id("io.github.rodrigotimoteo.mutkt") }
mutkt { targetClasses.add("com.example.*") }
```

Requires the Java or Kotlin JVM plugin. A test fails when this table drifts from the extension.

| Property | Type | Default | Meaning |
|---|---|---|---|
| `targetClasses` | List<String> | empty (all) | Class globs to mutate |
| `excludedClasses` | List<String> | empty | Class globs never mutated |
| `excludedMethods` | List<String> | empty | `method` or `pkg.Class#method` entries to skip |
| `operators` | Set<String> | empty (all) | Operator ids to enable |
| `threads` | Int | unset (available processors, resolved at run time) | Parallel test runs |
| `timeoutMs` | Long | 10000 | Per-mutant deadline in milliseconds |
| `maxMutantsPerClass` | Int | unset (unlimited) | Cap on mutants per class |
| `failBelowScore` | Int | unset (no gate) | Minimum score in percent (0..100) |
| `engines` | Set<String> | `jupiter` | JUnit Platform engines to run |
| `includeTags` | Set<String> | empty | Only run tests with these tags |
| `excludeTags` | Set<String> | empty | Skip tests with these tags |

`reports { }`:

| Property | Type | Default | Meaning |
|---|---|---|---|
| `console` | Boolean | true | Print the summary to the build log |
