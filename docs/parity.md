# Parity with legacy 0.3.3

## Method

- Every operator is compared, default-on or opt-in (see [Operators](operators.md)); parity does not depend on the default set.
- New sites are those of `discover`, after the noise filters (see [Operators](operators.md#noise-filters)).
- Per operator, sites are compared as multisets of (operator, class, method+descriptor, line, opcode): a static legacy table against the new engine's discovery over the seven fixtures in `mutkt-asm/src/test/resources/parity` (`ParityArithmetic`, `ParityClasses`, `ParityConditionals`, `ParityEmpties`, `ParityIdioms`, `ParityNulls`, `ParityReturns`). `ParityIdioms` holds the shapes of the Kotlin-specific operators: data class `copy` and `copy$default` calls, sealed and enum `when` (each branch on its own line, and all branches on one line), suspend functions and a coroutine builder (stdlib `startCoroutine` and `sequence`), and safe call, elvis, `!!` and `lateinit`.
- The legacy table `mutkt-asm/src/test/resources/parity/legacy-0.3.3.tsv` was generated once by `tools/parity-legacy` from mutation-core 0.3.3; its header holds the jar SHA-1 and versions.
- Legacy line `-1` means unknown and matches any line of the same site.
- The fixture-bytes hash check is informational: the tool compiled against kotlin-stdlib 2.1.10, the repo resolves a newer one, and only the `ParityClasses` facade differs.
- Regenerate: `./gradlew -p tools/parity-legacy run --args="$PWD/mutkt-asm/src/test/resources/parity"` (see `tools/parity-legacy/README.md`).
- Legacy SEALED_WHEN maps to `sealed-when`, NULL_SAFETY to `null-safety` (no deviation: every legacy row has a new site), DATA_CLASS_COPY to `data-class-copy` and COROUTINE to `coroutine`.
- Legacy DATA_CLASS_COPY is order-dependent: it fires on `copy`/`copy$default` only after a `componentN` method was seen in the calling class (a Kotlin class, data class or not). The fixture class `Wrapper` declares `component1()` first, so it is the only caller the legacy table has rows for (D15 covers the other callers).

## Deviations

Every documented difference, from `mutkt-asm/src/test/kotlin/com/github/rodrigotimoteo/mutkt/asm/Deviations.kt`. "Legacy only" rows exist in 0.3.3 and not in the rebuild; "new only" rows exist in the rebuild and not in 0.3.3.

| id | operator | side | what differs | why/intent |
|---|---|---|---|---|
| D1a | return-vals | legacy only | Also fires on every `ARETURN` (reference returns) | Rebuild is numeric only |
| D1b | return-vals | legacy only | Also fires on boolean `IRETURN` | Boolean returns belong to true-returns/false-returns |
| D1c | return-vals | legacy only | Fires on a return of a literal zero (`0` to `0`) | Rebuild skips it as an equivalent mutant |
| D2a | null-returns | legacy only | Nulls array and `java.util` collection returns | Rebuild leaves them to empty-returns |
| D2b | null-returns | legacy only | Nulls the `Object` result of suspend functions | It may carry `COROUTINE_SUSPENDED` |
| D2c | null-returns | legacy only | Nulls `kotlin.Unit` returns | Unit has a single value, so rebuild skips them |
| D2d | null-returns | legacy only | Fires on a literal null return (`null` to `null`), including the unreachable one after the throw of a `lateinit` getter (same line and opcode as the reachable return, so the match is by method and the multiset leaves only the surplus row) | Rebuild skips it as equivalent |
| D3a | empty-returns | new only | Empties `java.util.Collection` returns | Legacy has no Collection support |
| D3b | empty-returns | legacy only | Empties an already empty return | Rebuild skips it (empty factory, `new T[0]`) |
| D4 | negate-conditionals | legacy only | Negates `IFNULL`/`IFNONNULL` | Null checks belong to `null-safety` |
| D5 | arithmetic | legacy only | Treats `IINC` as arithmetic | Rebuild's increments operator owns it |
| D6a | constructor-calls | legacy only | Mutates a directly returned construction | Rebuild leaves it to null-returns |
| D6b | constructor-calls | legacy only | Mutates a directly thrown construction (`throw X()`), also the compiler's `NoWhenBranchMatchedException` and the `IllegalStateException` of coroutine state machines | Rebuild skips it as noise |
| D7 | void-method-calls | legacy only | Removes `Intrinsics.checkNotNull*` and `ResultKt.throwOnFailure` (rows without a line, suspend functions, `javaEmptyList`/`javaEmptyCollection`, the `!!` in `force` and the `lateinit` getters) | Rebuild skips them (`null-safety` owns the `Intrinsics` null checks) |
| D8a | non-void-method-calls | legacy only | Mutates super calls (`INVOKESPECIAL`) | Rebuild only mutates private calls on this |
| D8b | non-void-method-calls | legacy only | Mutates coroutine plumbing (suspend calls, `COROUTINE_SUSPENDED`, boxing, the `create(Continuation)` call in the `invoke` of a suspend lambda, suspend calls such as `yield` in `invokeSuspend`) | Rebuild skips it (suspend calls belong to `coroutine`) |
| D8c | non-void-method-calls | legacy only | Mutates the `ordinal()` call that indexes the `$WhenMappings` table of an enum `when` | Compiler plumbing, skipped by the `WhenMappingsFilter` noise filter |
| D9a | true-returns | legacy only | Fires on a constant true return (`ICONST_1` before `IRETURN`) | Rebuild skips it (true to true) |
| D9b | false-returns | legacy only | Fires on a constant false return | Rebuild skips `IRETURN` after `ICONST_0` (false to false) |
| D9c | true-returns | new only | `IRETURN` after `ICONST_0` | Legacy reports these as FALSE_RETURNS; the rebuild also makes them return true |
| D9d | false-returns | new only | `IRETURN` after `ICONST_1` | Legacy reports these as TRUE_RETURNS; the rebuild makes them return false |
| D10 | sealed-when | new only | A site for every `when` branch (`INSTANCEOF`, object `areEqual`, enum switch case) | Legacy mutates only the 2nd+ `INSTANCEOF` on one source line, or a switch after one, so it finds one site in the fixtures (`evalInline`, its second branch, which the rebuild also reports) |
| D11 | coroutine | new only | Suspend calls (`INVOKESTATIC`/`INVOKEVIRTUAL` with a trailing `Continuation`) | Legacy's suspend test (`descriptor.endsWith("Continuation;")`) cannot match a real descriptor, which ends with its return type. Its only row is the `getCOROUTINE_SUSPENDED()` marker call, which the rebuild never mutates: replacing it by null made a real suspension resume a completed continuation (`ClassCastException` in `runBlocking`) |
| D11b | coroutine | legacy only | Mutates the `create(Continuation)` call in the `invoke` of a suspend lambda (its descriptor ends in `Continuation`) and the `COROUTINE_SUSPENDED` marker in `invokeSuspend` | Compiler plumbing; mutating it breaks the state machine, the rebuild never does |
| D12a | constructor-calls | legacy only | Mutates constructions inside inlined stdlib bodies (the `ArrayList` of `map`/`filter`) | Compiler copy, skipped by the `InlinedBodyFilter` noise filter |
| D12b | negate-conditionals | legacy only | Negates the loop conditions of inlined stdlib bodies | As D12a |
| D12c | non-void-method-calls | legacy only | Mutates iterator, boxing and helper calls inside inlined stdlib bodies | As D12a |
| D13 | non-void-method-calls | legacy only | Mutates each compiler copy of a finally body (return, normal and exception path) as its own site: 3 rows for the `Registry.register(s)` call in `guarded` | One site per finally body; `InlinedFinallyFilter` keeps the first copy and `mutate` changes all copies together |
| D14a | non-void-method-calls | legacy only | Mutates data class `copy` and `copy$default` calls (`INVOKESTATIC` for the bridge, `INVOKEVIRTUAL` for `copy`, in the named methods only; the `getA()` call stays checked) | Rebuild leaves them to `data-class-copy` |
| D14b | non-void-method-calls | legacy only | Mutates `Intrinsics.areEqual` (object branch of a when, generated equals); matches `Pair2.equals`, `evalInline` and the `Zero ->` line of `eval`, never the recursive user call `eval(e.e)` | The rebuild skips it (`kotlin/jvm/internal` plumbing) |
| D15 | data-class-copy | new only | Fires on `copy` calls in a data class's own methods and in top-level functions | Legacy needs a `componentN` seen earlier in the calling class, which neither has; the rebuild fires on every copy call |
| D16a | arithmetic | legacy only | Mutates the members kotlinc generates for the data classes `Point` and `Pair2` (`IADD`/`IMUL` of the generated `hashCode`; legacy rows without a line) | Generated, so the rebuild skips them (see Noise filters in `docs/operators.md`) |
| D16b | negate-conditionals | legacy only | Mutates the members kotlinc generates for the data classes `Point` and `Pair2` (the comparisons of the generated `equals`; legacy rows without a line) | Generated, so the rebuild skips them (see Noise filters in `docs/operators.md`) |
| D16c | non-void-method-calls | legacy only | Mutates the members kotlinc generates for the data classes `Point` and `Pair2` (the `hashCode()` calls of the generated `hashCode`; legacy rows without a line) | Generated, so the rebuild skips them (see Noise filters in `docs/operators.md`) |
| D16d | null-returns | legacy only | Mutates the members kotlinc generates for the data classes `Point` and `Pair2` (the returns of the generated `toString`, `copy` and object `componentN`; legacy rows without a line) | Generated, so the rebuild skips them (see Noise filters in `docs/operators.md`) |
| D16e | null-safety | legacy only | Mutates the members kotlinc generates for the data classes `Point` and `Pair2` (the parameter check of the generated `copy`; legacy rows without a line) | Generated, so the rebuild skips them (see Noise filters in `docs/operators.md`) |
| D16f | return-vals | legacy only | Mutates the members kotlinc generates for the data classes `Point` and `Pair2` (the returns of the generated `hashCode` and numeric `componentN`; legacy rows without a line) | Generated, so the rebuild skips them (see Noise filters in `docs/operators.md`) |

The legacy COROUTINE row has no line, so it pairs with one of the two suspend calls of `chain` (same site, any line) and D11 explains the other: the marker row itself is not matched by a rebuild site.

Broad deviations: D12a to D12c match the inlined stdlib rows of `ParityClasses.lambdas` (line 60 or later, or an `INVOKESTATIC`), D8b matches any `INVOKESTATIC` in a suspend function (descriptor ending in `Continuation`, or `invokeSuspend`) and the `INVOKEVIRTUAL` calls of `invoke`/`invokeSuspend` there, D9c/D9d any boolean site with that constant, D10 every `sealed-when` site, D11 every suspend-call site, D15 every `copy` site in the listed methods; D11b (suspend `invoke` virtual call, `invokeSuspend` static marker), D14a/D14b and D2d are class, method and opcode based, so inside those methods the side is effectively unchecked. Legacy true/false rows record the constant before `IRETURN`; the test describes new sites the same way so a site pairs only with the same constant.
