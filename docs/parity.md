# Parity with legacy 0.3.3

## Method

- Per operator, sites are compared as multisets of (operator, class, method+descriptor, line, opcode): a static legacy table against the new engine's discovery over the six fixtures in `mutkt-asm/src/test/resources/parity` (`ParityArithmetic`, `ParityClasses`, `ParityConditionals`, `ParityEmpties`, `ParityNulls`, `ParityReturns`).
- The legacy table `mutkt-asm/src/test/resources/parity/legacy-0.3.3.tsv` was generated once by `tools/parity-legacy` from mutation-core 0.3.3; its header holds the jar SHA-1 and versions.
- Legacy line `-1` means unknown and matches any line of the same site.
- The fixture-bytes hash check is informational: the tool compiled against kotlin-stdlib 2.1.10, the repo resolves a newer one, and only the `ParityClasses` facade differs.
- Regenerate: `./gradlew -p tools/parity-legacy run --args="$PWD/mutkt-asm/src/test/resources/parity"` (see `tools/parity-legacy/README.md`).
- Legacy SEALED_WHEN maps to `sealed-when`; NULL_SAFETY maps to `null-safety` with no deviation.
- Legacy DATA_CLASS_COPY fired only when the calling class was itself a data class (order-dependent detection); `data-class-copy` fires in any class. It has a counterpart but stays unmapped: no parity fixture calls `copy`, so the legacy table has no rows to compare, and it is not a deviation entry.

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
| D2d | null-returns | legacy only | Fires on a literal null return (`null` to `null`) | Rebuild skips it as equivalent |
| D3a | empty-returns | new only | Empties `java.util.Collection` returns | Legacy has no Collection support |
| D3b | empty-returns | legacy only | Empties an already empty return | Rebuild skips it (empty factory, `new T[0]`) |
| D4 | negate-conditionals | legacy only | Negates `IFNULL`/`IFNONNULL` | Null checks belong to `null-safety` |
| D5 | arithmetic | legacy only | Treats `IINC` as arithmetic | Rebuild's increments operator owns it |
| D6a | constructor-calls | legacy only | Mutates a directly returned construction | Rebuild leaves it to null-returns |
| D6b | constructor-calls | legacy only | Mutates a directly thrown construction (`throw X()`) | Rebuild skips it as noise |
| D7 | void-method-calls | legacy only | Removes `Intrinsics.checkNotNull*` and `ResultKt.throwOnFailure` (rows without a line, suspend functions, `javaEmptyList`/`javaEmptyCollection`) | Rebuild skips them (`null-safety` owns the `Intrinsics` null checks) |
| D8a | non-void-method-calls | legacy only | Mutates super calls (`INVOKESPECIAL`) | Rebuild only mutates private calls on this |
| D8b | non-void-method-calls | legacy only | Mutates coroutine plumbing (suspend calls, `COROUTINE_SUSPENDED`, boxing) | Rebuild skips it |
| D9a | true-returns | legacy only | Fires on a constant true return (`ICONST_1` before `IRETURN`) | Rebuild skips it (true to true) |
| D9b | false-returns | legacy only | Fires on a constant false return | Rebuild skips `IRETURN` after `ICONST_0` (false to false) |
| D9c | true-returns | new only | `IRETURN` after `ICONST_0` | Legacy reports these as FALSE_RETURNS; the rebuild also makes them return true |
| D9d | false-returns | new only | `IRETURN` after `ICONST_1` | Legacy reports these as TRUE_RETURNS; the rebuild makes them return false |
| D10 | sealed-when | new only | A site for every `when` branch (`INSTANCEOF`, object `areEqual`, enum switch case) | Legacy mutates only the 2nd+ `INSTANCEOF` on one source line, or a switch after one, so it finds no site in the fixtures (every branch is on its own line) |
| D11 | coroutine | new only | Suspend calls (`INVOKESTATIC` with a trailing `Continuation`) | Legacy's suspend test (`descriptor.endsWith("Continuation;")`) cannot match a real descriptor, which ends with its return type. Its only row is the `getCOROUTINE_SUSPENDED()` marker call, which the rebuild never mutates: replacing it by null made a real suspension resume a completed continuation (`ClassCastException` in `runBlocking`) |

The legacy COROUTINE row has no line, so it pairs with one of the two suspend calls of `chain` (same site, any line) and D11 explains the other: the marker row itself is not matched by a rebuild site.

Broad deviations: D8b matches any `INVOKESTATIC` in a suspend function, D9c/D9d any boolean site with that constant, and D10 every `sealed-when` site (legacy has no rows to pair with), so for those rows the side is effectively unchecked. Legacy true/false rows record the constant before `IRETURN`; the test describes new sites the same way so a site pairs only with the same constant.
