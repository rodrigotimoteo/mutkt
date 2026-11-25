# Operators

Operators run when `operators` is empty (the default) are marked default-on; the others are opt-in until proven on real projects, and run only when their id is listed (`mutkt { operators.addAll(...) }`, see [dsl.md](dsl.md)). Listing ids selects exactly those: to run every operator, list all of them.

| Id | Default | Summary |
|---|---|---|
| `negate-conditionals` | yes | Inverts an integer or reference comparison |
| `arithmetic` | yes | Swaps `+`/`-`, `*`/`/`, `%` to `*` |
| `conditionals-boundary` | yes | Moves a comparison boundary (`<` to `<=`) |
| `null-returns` | yes | Returns null from an object method |
| `return-vals` | yes | Returns zero from a numeric method |
| `empty-returns` | yes | Returns an empty array or collection |
| `constructor-calls` | no | Replaces a construction with null |
| `increments` | no | Flips the sign of `i++` / `i += n` |
| `true-returns` | no | Returns true from a boolean method |
| `false-returns` | no | Returns false from a boolean method |
| `void-method-calls` | no | Removes a void call |
| `non-void-method-calls` | no | Replaces a call's result by its default value |
| `sealed-when` | no | Makes one `when` branch unreachable |
| `null-safety` | no | Flips null branches, drops Kotlin null checks |
| `data-class-copy` | no | Replaces a data class `copy` call by null |
| `coroutine` | no | Replaces a coroutine builder or suspend call by its default value |

Opt-in operators have sites in most Kotlin code and several yield mostly equivalent mutants (calls with no observable effect, parameter checks), so enabling them is a deliberate choice. Parity with the legacy engine is checked for every operator, default or not ([parity.md](parity.md)).

## Reference

| Id | Bytecode change | Kotlin before / after | Known equivalent mutants |
|---|---|---|---|
| `negate-conditionals` | `IFEQ`/`IFNE`, `IFLT`/`IFGE`, `IFGT`/`IFLE`, `IF_ICMPEQ`/`IF_ICMPNE`, `IF_ICMPLT`/`IF_ICMPGE`, `IF_ICMPGT`/`IF_ICMPLE`, `IF_ACMPEQ`/`IF_ACMPNE` swap to their negation | `if (a < b)` compiles to `IF_ICMPGE`, mutated to `IF_ICMPLT` (condition inverted) | Rare; a condition guarding two identical branches. `IFNULL`/`IFNONNULL` are never emitted: null checks belong to `null-safety`, which is Kotlin-only, so no operator mutates them in a Java class (intended). |
| `arithmetic` | I/L/F/D `ADD`<->`SUB`, `MUL`<->`DIV`, `REM`->`MUL`. Unary `NEG`, `IINC` and string concatenation are not sites. | `a + b` / `a - b`; `a * b` / `a / b`; `a % b` / `a * b` | `x + 0`, `x - 0`, `x * 1`, `x / 1`; `x * 0` vs `x / 0` is not equivalent for int (throws). |
| `conditionals-boundary` | `IFGE`<->`IFGT`, `IFLE`<->`IFLT`, `IF_ICMPGE`<->`IF_ICMPGT`, `IF_ICMPLE`<->`IF_ICMPLT`. Long and double compare via `LCMP`/`DCMPx` then `IFxx`, so they are covered by the `IF*` rows. `IF_ACMP*` is not mutated. | `a < b` / `a <= b`; `x >= 0` / `x > 0` | Loop or range bounds where the equal case is unreachable (`i < n` with `i != n` guaranteed); `> Int.MAX_VALUE`-style guards |
| `null-returns` | `ARETURN` becomes `POP`, `ACONST_NULL`, `ARETURN` for methods returning an object type, except arrays and `java.util` `List`, `Set`, `Map`, `Collection` (left to `empty-returns`). An `ARETURN` directly after `ACONST_NULL` is not a site. | `return name` / `return null` (also for Kotlin non-null return types, where callers fail with an NPE) | Methods whose result callers never use; a return already null on every path that reaches it through a merge label |
| `return-vals` | `IRETURN`/`LRETURN`/`FRETURN`/`DRETURN` become `POP`/`POP2`, zero constant, same return. Boolean (`Z`) returns, reference returns, `void` and returns directly after a zero constant are not sites. | `return x` / `return 0` (`0L`, `0f`, `0.0`) | Primitive fields/values that are already zero |
| `empty-returns` | `ARETURN` becomes `POP`, an empty value, `ARETURN` for methods returning an array (`new T[0]`, component type kept) or `java.util` `List`, `Set`, `Map`, `Collection` (`Collections.emptyList()`/`emptySet()`/`emptyMap()`; `Collection` gets an empty list). The empty value is the shared `java.util.Collections` singleton, or a fresh zero-length array. A return directly after an empty-collection factory call or a zero-length array creation is not a site. | `return items` / `return emptyList()` | Methods whose result callers never use; a return already empty on every path that reaches it through a merge label |
| `constructor-calls` | The whole `NEW`, `DUP`, args, `INVOKESPECIAL <init>` sequence is replaced by the args popped (`POP`/`POP2` per descriptor) and `ACONST_NULL`. Nested constructions are separate sites (inner first). `super()`/`this()` calls, `StringBuilder` constructions (string templates), constructions directly followed by `ATHROW` (`throw X()`) or `ARETURN` (a returned construction is `null-returns` territory) and any other shape are not sites. | `Box(a)` / `null` (callers fail with an NPE, or null reaches a null-checked parameter) | Objects whose constructor has no effect and whose value is never used |
| `increments` | `IINC` with a non-zero increment has its sign flipped (`i++` to `i--`, `i += 5` to `i -= 5`). Wide `IINC` (increments beyond a byte) is flipped too, except -32768, which has no positive counterpart. Kotlin emits `IINC` only for small int constants (`i += 1000` is `IADD`, a site of `arithmetic`); long, float and double increments are also `ADD`. | `i++` / `i--`; loop counters of `for`, `while` and `do` | A counter whose value is never read; loops that then never end are reported as TIMEOUT, not equivalent |
| `true-returns` | In methods whose descriptor ends in `Z`, `IRETURN` becomes `POP`, `ICONST_1`, `IRETURN`, whatever computed the value (constant, local, comparison, call). An `IRETURN` directly after `ICONST_1` is not a site. | `return a > 0` / `return true` | Methods that already return true on every path through a merge label; unused results. Shares each `IRETURN` with `false-returns` but the mutants differ. |
| `false-returns` | Same as `true-returns` with `ICONST_0`. | `return a > 0` / `return false` | As `true-returns` with false |
| `void-method-calls` | An `INVOKEVIRTUAL`/`INVOKEINTERFACE`/`INVOKESTATIC` returning `void` is replaced by pops for its arguments (`POP2` for long/double) and receiver, or a `NOP` when nothing is on the stack. Void `JobKt` statics (`ensureActive`) belong to `coroutine`. `INVOKESPECIAL` is a site only for a private method of the class itself (the call's owner is the class being mutated; no debug info needed); constructors and super calls, `invokedynamic` and compiler plumbing (`kotlin/jvm/internal/*` such as `Intrinsics`, `kotlin/coroutines/intrinsics/*`, `kotlin/coroutines/jvm/internal/*`, `ResultKt`) are not sites. | `log(x)` / (call removed) | Calls without observable effect (logging, caches, idempotent setters). String templates compile to `StringBuilder.append`/`toString` calls and `Intrinsics.areEqual` backs `==`: all become noisy, mostly equivalent mutants, so `kotlin/jvm/internal/*` (including `Intrinsics`) is skipped but `StringBuilder` calls are not. |
| `non-void-method-calls` | An invoke returning a value is replaced by the same pops and the return type's default: `ICONST_0` (int, boolean, byte, char, short), `LCONST_0`, `FCONST_0`, `DCONST_0`, `ACONST_NULL` (objects and arrays). `kotlin/Unit` returns and suspend calls (last parameter `Continuation`) are not sites; exclusions as for `void-method-calls`; data class `copy` calls belong to `data-class-copy`; coroutine builder and suspend calls belong to `coroutine`. | `val n = repo.count()` / `val n = 0`; `name()` / `null` (callers fail with an NPE) | Results already zero/null; results that are discarded or only used by logging. Overlaps by effect, not by bytes, with `null-returns`/`return-vals` for `return f()`. |
| `sealed-when` | Makes one `when` branch unreachable. A type test becomes false (`INSTANCEOF` is replaced by `POP`, `ICONST_0`; the object-branch `Intrinsics.areEqual(subject, X.INSTANCE)` by `POP2`, `ICONST_0`) when the same local is tested at least twice in the method, so a branch is skipped for the next one or the else path. An enum `when` (`TABLESWITCH`/`LOOKUPSWITCH` on `ordinal()`: with the `$WhenMappings` array, or directly when the `ordinal()` owner is an enum class the generator can read from the analysed classes, the classpath or the JDK; an owner it cannot read is not assumed to be an enum, so a direct `when (e.ordinal)` over it has no site, and a plain class with its own `ordinal()` method is never one) gets one site per body: its cases are redirected to the switch default (cases already going to the default, such as the unmapped ordinal 0 of a nullable `when`, are not sites). A lone type test, tests on different locals, a one-entry enum `when` (a plain comparison, left to `negate-conditionals`) and Int or String switches are not sites. | `is Circle -> a` / branch never taken: falls to the next branch, the else, or `NoWhenBranchMatchedException`; `Color.RED -> 1` / `RED` goes to the default | A branch no test reaches; a branch whose body equals the else body; the last branch of an exhaustive when whose type the subject can never have. The rule fires on any two type tests of one local, sealed hierarchy or not (the operator sees no class hierarchy), and a reused local slot can credit tests of two unrelated variables to one chain. Site descriptions name the switch key (a `$WhenMappings` index), not an enum entry or ordinal. |
| `null-safety` | `IFNULL`<->`IFNONNULL` swap. A call to `kotlin/jvm/internal/Intrinsics.checkNotNull`, `checkNotNullParameter`, `checkNotNullExpressionValue` or `throwUninitializedPropertyAccessException` is replaced by pops for its arguments (the compiler follows the lateinit throw with `ACONST_NULL`, so the getter then returns null). `throwNpe`/`throwJavaNpe` are not sites. Only classes carrying `@kotlin.Metadata` have sites (a Java null check is the author's own: no operator, this one or `negate-conditionals`, mutates a Java `IFNULL`/`IFNONNULL`, by design); methods the framework skips (constructors) get no site. | `s?.length`, `s ?: d`, `s == null` flip their branch; `s!!`, a parameter check, a platform-type result check or a `lateinit` read stops failing fast | Parameter checks (`checkNotNullParameter`) are typically equivalent for Kotlin callers, which cannot pass null, so removing them only matters for Java or reflective callers; a platform check whose value is never dereferenced; a flipped branch where both arms agree. Constructor parameter checks produce no sites (constructors are skipped centrally), which legacy 0.3.3 did not do. |
| `data-class-copy` | A call recognised as a data class copy is replaced by pops for its arguments (`POP2` for long/double) and receiver, then `ACONST_NULL`. Two shapes: `INVOKEVIRTUAL owner.copy(...)` with at least one argument returning `owner`, and `INVOKESTATIC owner.copy$default(owner, ..., int, Object)` returning `owner` (the default-argument bridge). The data class is the callee's, in a class the operator never sees (only the class under mutation is known), so the rule is structural: a plain class with a same-shaped `copy` method is a site too. Independent of the order of methods and classes. Lookalikes that are not data classes (`Bitmap.copy`, a user `copy` returning its own type) are claimed by this operator, not by `non-void-method-calls`. A value class property mangles the name to `copy-<hash>`: such calls are not matched and stay `non-void-method-calls` sites. | `p.copy(x = 1)` / `null` (callers fail with an NPE) | Copies whose result is unused. For `return x.copy(...)` the effect overlaps with `null-returns`/`return-vals` (different instructions, same visible outcome). Calls of this shape are not sites of `non-void-method-calls` (it would emit the same mutant). |
| `coroutine` | A static call on `kotlinx/coroutines/BuildersKt` (`launch`, `async`, `runBlocking`, `withContext`) or `JobKt` (`Job()`, `ensureActive`), or a call of a suspend function (`INVOKESTATIC`/`INVOKEVIRTUAL`/`INVOKEINTERFACE`, or `INVOKESPECIAL` of a private method of the class itself, whose descriptor ends `Lkotlin/coroutines/Continuation;)Ljava/lang/Object;`, or `Continuation;` followed by one `I` per default mask (one per 32 parameters) and `Ljava/lang/Object;)Ljava/lang/Object;` for the `foo$default` bridge of a suspend call with default arguments), is replaced by pops for its arguments and receiver and the return type's default (null for objects, nothing for `void`). The suspension marker `IntrinsicsKt.getCOROUTINE_SUSPENDED()`, the `kotlin/coroutines/intrinsics/*` and `jvm/internal/*` helpers, `ResultKt.throwOnFailure` and `Boxing` are never sites, so label switches and suspension checks stay as the compiler wrote them: the replaced call simply completes at once with null. The call that resumes a suspend function from its continuation class (an `invokeSuspend(Object)` of a class whose superclass is `kotlin/coroutines/jvm/internal/ContinuationImpl`; suspend lambdas extend `SuspendLambda` and keep their sites) is not a site. `GlobalScope` has no statics: `GlobalScope.launch` is a `BuildersKt` call. Independent of the order of methods. | `val j = launch { work() }` / `val j = null` (`j.join()` fails with an NPE); `launch { work() }` as a statement / the launch is dropped; `val n = fetch()` / `null` (callers unboxing it fail with an NPE) | `delay(...)` (a skipped delay changes timing only); calls whose result is unused and whose effect is covered by `launch` fire-and-forget tests that do not wait. Needs kotlinx.coroutines on the mutated code's classpath to produce builder sites; suspend calls need only the stdlib. Calls of this shape are not sites of `void-method-calls` or `non-void-method-calls` (they would emit the same mutant). |

## Examples

Each entry shows the source-level meaning of one mutant. Details and edge cases are in the reference table above.

### `negate-conditionals`
```kotlin
if (a < b) take() else skip()   // mutant: if (a >= b) take() else skip()
```
Equivalent when both branches do the same thing.

### `arithmetic`
```kotlin
val t = a + b                   // mutant: a - b   (a * b to a / b, a % b to a * b)
```
Equivalent for `x + 0`, `x - 0`, `x * 1`, `x / 1`.

### `conditionals-boundary`
```kotlin
if (x >= 0) ok()                // mutant: if (x > 0) ok()
```
Equivalent when the equal case is unreachable (`i < n` with `i != n` guaranteed).

### `null-returns`
```kotlin
fun name(): String = user.name  // mutant: always returns null (callers fail with an NPE)
```
Equivalent when callers never use the result.

### `return-vals`
```kotlin
fun total(): Int = sum          // mutant: fun total(): Int = 0
```
Equivalent when the value is already zero.

### `empty-returns`
```kotlin
fun items(): List<Item> = list  // mutant: returns an empty list (an empty array for arrays)
```
Equivalent when callers never use the result or it is already empty.

### `constructor-calls`
```kotlin
val b = Box(a)                  // mutant: val b = null
```
Equivalent for an effect-free constructor whose value is never used.

### `increments`
```kotlin
var i = 0; while (i < n) { i++ }   // mutant: i--   (i += 5 becomes i -= 5)
```
Equivalent when the counter is never read; a loop that no longer ends reports TIMEOUT.

### `true-returns`
```kotlin
fun positive(a: Int) = a > 0    // mutant: fun positive(a: Int) = true
```
Equivalent when the method already returns true on every path.

### `false-returns`
```kotlin
fun positive(a: Int) = a > 0    // mutant: fun positive(a: Int) = false
```
As `true-returns`, with false.

### `void-method-calls`
```kotlin
log(x)                          // mutant: (call removed)
```
Equivalent for calls without observable effect (logging, caches, idempotent setters).

### `non-void-method-calls`
```kotlin
val n = repo.count()            // mutant: val n = 0   (null for objects)
```
Equivalent when the result is already zero or null, or only used by logging.

### `sealed-when`
```kotlin
when (s) { is Circle -> a; is Square -> b; else -> c }   // mutant: the Circle test is always false
```
Needs two type tests on the same subject (a lone `is` is not a site). Equivalent when no test reaches the branch or its body equals the else body.

### `null-safety`
```kotlin
val len = s?.length             // mutant: null branch flipped; s!! or a parameter check stops failing fast
```
Parameter checks are typically equivalent for Kotlin callers, which cannot pass null.

### `data-class-copy`
```kotlin
val q = p.copy(x = 1)           // mutant: val q = null
```
Equivalent when the copy is unused.

### `coroutine`
```kotlin
val j = launch { work() }       // mutant: val j = null   (j.join() fails with an NPE; the call is BuildersKt.launch$default)
val n = fetch()                 // mutant: val n = null   (a suspend call completes at once)
```
`delay(...)` only changes timing; fire-and-forget `launch` that tests do not wait for is equivalent.

## Noise filters

Site filters (`mutkt-asm/.../asm/filters`) recognise compiler-generated instructions only; user code is never filtered. They run inside site enumeration (`sitesOf`), so `discover` and `mutate` see the same list and an ordinal is an index into the filtered sites. Operators do not know about filters.

| Rule | Recognised by | Sites removed (corpus / parity) |
|---|---|---|
| `InlinedBodyFilter` | Instructions whose innermost kotlinc marker is a `$i$f$name` local variable (an inline function body copied into a caller). The marker named after the method itself (the inline function's own body) and `$i$a$` lambda bodies, which the user wrote, stay. | 33 / 22 |
| `InlinedClassFilter` | In a regenerated `Caller$f$$inlined$g$1` class, everything outside `$i$a$` lambda bodies. Without debug info nothing is recognised. | 4 / 0 |
| `InlinedFinallyFilter` | kotlinc copies a finally body to the normal path, to every `return`/`break`/`continue` exit and to the catch-any handler. Per handler (a try-catch block without a type) the template is the handler's code after its `ASTORE e` up to `ALOAD e; ATHROW`. The copies start at the end of each try range that targets the handler, and before each `GOTO` to the end of the handler (the normal path has no range of its own when the try body ends just before it). A copy matches when it has the same opcodes and operands, locals the body declares renamed one-to-one (slots it reads before writing must be the same), and jumps landing at the same offset inside it (or at the end of the finally body). Every instruction of the copies after the first (in instruction order) is noise; the first copy keeps the site, its ordinal and its line. `mutate` applies the operator to the first copy and to the same-description sites of the other copies, so a finally site is one mutant that changes every path. No debug info needed. If any copy after a try range differs, or copies overlap another group, or the bytecode has an unexpected shape, nothing is grouped for that handler and the copies stay separate mutants. A finally that returns or throws on its own has no `ALOAD e; ATHROW` end and is not grouped. `use {}` is grouped too: its `closeFinally` is one site. A twin the operator does not report with the same description (an operator that looks beyond the copy) keeps its original code. | see `InlinedFinallyFilterTest` |
| `StringWhenFilter` | `s.hashCode()` before a switch whose every case leads to `s.equals("constant")`, and those `equals` calls (a `when` on a String with at least three branches). Each `equals` must have the local the hash was taken from as receiver, a String constant as argument, and an `IFNE` that is followed by the next guard or by the `GOTO` to the switch default. A user `when (s.hashCode())` is not matched, nor are cases that compare another receiver, return the result or branch on it differently. The branch jumps stay. A hand-written `switch (s.hashCode())` that spells the same guard chain would still be dropped (not seen in compiler output of ordinary code). | 8 / 0 |
| `WhenMappingsFilter` | Only the `ordinal()` call after `GETSTATIC X$WhenMappings.$EnumSwitchMapping$n; SWAP` (an enum `when`). The switch, the table lookup and the branches stay, so `sealed-when` sites on enum switches are not hidden (the sealed-when tests pass). The user's own `table[e.ordinal]` has no `SWAP`. | 4 / 1 |

Not filters, because nothing needs one: `$default` bridges and the `$WhenMappings` class (synthetic or only `<clinit>`, skipped by the skip rules: 0 sites before and after), and `Intrinsics.areEqual` and the lateinit `throwUninitializedPropertyAccessException` (`kotlin/jvm/internal/*` calls are not sites of the call operators; the null checks around them belong to `null-safety`).
A user-defined inline function is not mutated at all: `isMutable` skips a method whose own `$i$f$<its name>` marker runs to the end of the body (`isInlineFunctionOriginal`, shared with `InlinedBodyFilter`; the marker of a copy ends before the caller's return, so a regular method named like the inline function it calls stays mutable and its copy is filtered; mangled names of internal and value-class functions appear in the marker unchanged), so neither the original body nor its copies in callers are sites, and the original would otherwise leave survivors that only a Java or reflection call could kill. Callers' own code stays mutated. Blind spot: Java callers and function references of inline functions are not mutation-tested; a `::inlineFn` reference compiles to a regenerated class whose `invoke` holds a copy of the body (no call of the original). The marker is a local variable, so a class compiled without debug info still has its inline functions mutated.
Members kotlinc generates for a data class or data object (`equals(Object)`, `hashCode()`, `toString()`, `componentN()`, `copy(...)`; `copy$default` is synthetic anyway) are not mutated either: `mutableMethods` skips them for `discover` and `mutate` alike, because tests cannot meaningfully kill their mutants. kotlinc (K2, and K1 with the JVM IR backend) writes no line number into these members, while a member the user declares, such as a `toString` override in a data class or an `equals` of a regular class, has its own lines and stays mutated. The rule applies only to classes carrying `@kotlin.Metadata` with line numbers in some method: without debug info every method lacks lines, so nothing is recognised. A value class's `equals(Object)`, `hashCode()` and `toString()` wrappers have the same shape and are skipped too; its `equals-impl`, `hashCode-impl` and `toString-impl` statics stay mutated. Legacy 0.3.3 mutated the data class members (deviations D16a to D16f).

## Suppression

`@SuppressMutations(operators = [...], reason = "...")` on a class or function skips mutants: no `operators` means all, otherwise only the listed ids. Names are checked against every operator, enabled or not, so a suppression for a disabled operator is valid; an unknown name fails the run and lists all valid ids.
Class and function suppression are unioned. Nested, inner, companion and anonymous classes inherit from the enclosing class, and anonymous classes also from the enclosing function. Class filters never change this: suppressions are read from every class on the input, so targeting only `Outer$Inner` still honors `Outer`'s annotation.
A function's lambda bodies (`f$lambda$N`) are suppressed with it; they match by name, so every overload named `f` covers them. A function's own annotation matches name and descriptor.
Limits: inline function bodies are never mutated (see Noise filters), so suppressing one has no effect. Property accessors are suppressed only through their class.

See also: [Parity with legacy 0.3.3](parity.md)
