package com.github.rodrigotimoteo.mutkt.asm

internal enum class Side { LEGACY_ONLY, NEW_ONLY }

/** A documented, intended difference between legacy 0.3.3 and the rebuild: claims the rows [matches] selects. */
internal class Deviation(
    val id: String,
    val operator: String,
    val side: Side,
    val reason: String,
    val matches: (Row) -> Boolean,
)

private val Row.returnType get() = method.substringAfterLast(')')
private val Row.isBoolean get() = returnType == "Z"
private val Row.isSuspend get() =
    method.contains("Lkotlin/coroutines/Continuation;)") ||
        method.startsWith("invokeSuspend(")

private fun Row.inClass(vararg names: String) = className in names

private fun Row.inMethod(vararg names: String) = names.any { method.startsWith("$it(") }

// ParityClasses.lambdas: user code is on lines 56 to 59, the inlined stdlib bodies get lines of their own
// after it; the boxing of the lambda result on line 58 is part of the inlined map body.
private val Row.inInlinedBody get() = inMethod("lambdas") && (line >= 60 || opcode == "INVOKESTATIC")

// Methods whose only construction is a directly thrown one (exhaustive when, coroutine resume check).
private val THROWING_METHODS =
    arrayOf("area", "hex", "chain", "eval", "evalInline", "turn", "turnInline", "fetchTwice", "invokeSuspend")

// Methods whose Intrinsics null checks belong to null-safety: java platform types, `!!`, lateinit getters.
private val NULL_CHECK_METHODS = arrayOf("javaEmptyList", "javaEmptyCollection", "force", "getTitle", "getValue")

// Members kotlinc generates for the parity data classes; legacy rows for them have no line.
private val Row.inGeneratedDataMember get() =
    inClass("Point", "Pair2") &&
        line == -1 &&
        inMethod("equals", "hashCode", "toString", "component1", "component2", "copy")

private const val GENERATED_DATA_MEMBER_REASON =
    "legacy mutates the members kotlinc generates for a data class; the rebuild skips them"

// The parity suspend code, none of which has a user ===: the prologue of fetchTwice and chain (legacy rows without
// a line) and the COROUTINE_SUSPENDED checks there and in the generate and runIt lambdas.
private val Row.inStateMachine get() =
    (
        (inClass("ParityIdioms") && inMethod("fetchTwice")) ||
            (inClass("ParityNulls") && inMethod("chain")) ||
            (inClass("ParityIdioms\$generate\$1", "ParityIdioms\$runIt\$1") && inMethod("invokeSuspend"))
    ) &&
        (opcode == "IF_ACMPNE" || (line == -1 && opcode in setOf("ISUB", "IFEQ")))

private const val STATE_MACHINE_REASON =
    "legacy mutates the coroutine state machine kotlinc generates; the rebuild filters it"

private val Row.inGeneratedEnumMember get() = inClass("Color", "Dir") && inMethod("values", "valueOf", "getEntries")

private const val ENUM_MEMBER_REASON =
    "legacy mutates the members kotlinc generates for an enum; the rebuild skips them"

// kotlinc's @NotNull on the method, read from the compiled parity fixture.
private val Row.returnsNonNull get() =
    parityClasses.values.firstNotNullOfOrNull { it[className] }?.let(::parse)?.methods.orEmpty().any {
        it.name + it.desc == method && it.returnsNonNull()
    }

private val COLLECTIONS = setOf("Ljava/util/List;", "Ljava/util/Set;", "Ljava/util/Map;", "Ljava/util/Collection;")

private fun legacyOnly(
    id: String,
    operator: String,
    reason: String,
    matches: (Row) -> Boolean,
) = Deviation(id, operator, Side.LEGACY_ONLY, reason, matches)

private fun newOnly(
    id: String,
    operator: String,
    reason: String,
    matches: (Row) -> Boolean,
) = Deviation(id, operator, Side.NEW_ONLY, reason, matches)

internal object Deviations {
    val all: List<Deviation> =
        listOf(
            legacyOnly(
                "D1a",
                "return-vals",
                "legacy also fires on every ARETURN (reference returns); the rebuild is numeric only",
            ) {
                it.opcode == "ARETURN"
            },
            legacyOnly(
                "D1b",
                "return-vals",
                "legacy also fires on boolean IRETURN; boolean returns belong to true-returns/false-returns",
            ) {
                it.opcode == "IRETURN" && it.isBoolean
            },
            legacyOnly(
                "D1c",
                "return-vals",
                "legacy fires on a return of a literal zero (0 to 0); the rebuild skips it as an equivalent mutant",
            ) {
                it.opcode == "IRETURN" && !it.isBoolean && it.inMethod("early")
            },
            legacyOnly(
                "D2a",
                "null-returns",
                "legacy nulls array and java.util collection returns; the rebuild leaves them to empty-returns",
            ) {
                it.returnType.startsWith("[") || it.returnType in COLLECTIONS
            },
            legacyOnly(
                "D2b",
                "null-returns",
                "legacy nulls the Object result of suspend functions (it may carry COROUTINE_SUSPENDED)",
            ) {
                it.isSuspend
            },
            legacyOnly(
                "D2c",
                "null-returns",
                "legacy nulls kotlin.Unit returns; Unit has a single value, so the rebuild skips them",
            ) {
                it.returnType == "Lkotlin/Unit;"
            },
            legacyOnly(
                "D2d",
                "null-returns",
                "legacy fires on a literal null return (null to null); the rebuild skips it as equivalent",
            ) {
                it.inMethod("literalNull") || (it.inMethod("orNull") && it.line == 11)
            },
            legacyOnly("D2e", "null-returns", "legacy nulls non-null Kotlin returns, lateinit getters too") {
                it.returnsNonNull
            },
            newOnly(
                "D3a",
                "empty-returns",
                "legacy has no Collection support; the rebuild empties java.util.Collection returns",
            ) {
                it.returnType == "Ljava/util/Collection;"
            },
            legacyOnly(
                "D3b",
                "empty-returns",
                "legacy empties an already empty return; the rebuild skips it (empty factory, new T[0])",
            ) {
                it.inMethod("alreadyEmptyList", "alreadyEmptySet", "alreadyEmptyMap", "emptyIntArray", "emptyStrings")
            },
            newOnly("D3c", "empty-returns", "the rebuild returns \"\" from a non-null String method") {
                it.returnType == "Ljava/lang/String;"
            },
            legacyOnly(
                "D4",
                "negate-conditionals",
                "legacy negates IFNULL/IFNONNULL; null checks belong to null-safety",
            ) {
                it.opcode == "IFNULL" || it.opcode == "IFNONNULL"
            },
            legacyOnly(
                "D5",
                "arithmetic",
                "legacy treats IINC as arithmetic; the rebuild's increments operator owns it",
            ) {
                it.opcode == "IINC"
            },
            legacyOnly(
                "D6a",
                "constructor-calls",
                "legacy mutates a directly returned construction; the rebuild leaves it to null-returns",
            ) {
                it.inMethod("copyWith", "arrayList", "copy", "plus")
            },
            legacyOnly(
                "D6b",
                "constructor-calls",
                "legacy mutates a directly thrown construction (throw X(), also the compiler's exhaustive-when " +
                    "and coroutine resume exceptions); the rebuild skips it as noise",
            ) {
                it.inMethod(*THROWING_METHODS) && (it.line != -1 || it.inMethod("area", "hex", "chain"))
            },
            legacyOnly(
                "D7",
                "void-method-calls",
                "legacy removes Intrinsics.checkNotNull* and ResultKt.throwOnFailure; the rebuild skips them",
            ) {
                // Parameter checks run before the first line number; the other rows are plumbing in named methods.
                it.opcode == "INVOKESTATIC" &&
                    (it.line == -1 || it.isSuspend || it.inMethod(*NULL_CHECK_METHODS))
            },
            legacyOnly(
                "D8a",
                "non-void-method-calls",
                "legacy mutates super calls (INVOKESPECIAL); the rebuild only mutates private calls on this",
            ) {
                it.opcode == "INVOKESPECIAL"
            },
            legacyOnly(
                "D8b",
                "non-void-method-calls",
                "legacy mutates coroutine plumbing (suspend calls, COROUTINE_SUSPENDED, Boxing, the create call of a " +
                    "suspend lambda's invoke); the rebuild skips it",
            ) {
                it.isSuspend &&
                    (
                        it.opcode == "INVOKESTATIC" ||
                            (it.opcode == "INVOKEVIRTUAL" && it.inMethod("invoke", "invokeSuspend"))
                    )
            },
            legacyOnly(
                "D8c",
                "non-void-method-calls",
                "legacy mutates the ordinal() call that feeds the `\$WhenMappings` table of an enum when; skipped here",
            ) {
                it.inMethod("hex", "turn", "turnInline", "kind")
            },
            legacyOnly(
                "D9a",
                "true-returns",
                "legacy fires on a constant true return; the rebuild skips IRETURN right after ICONST_1 (true to true)",
            ) {
                it.opcode == "ICONST_1"
            },
            legacyOnly(
                "D9b",
                "false-returns",
                "legacy fires on a constant false return; the rebuild skips IRETURN after ICONST_0 (false to false)",
            ) {
                it.inMethod("aFalse", "equals")
            },
            newOnly(
                "D9c",
                "true-returns",
                "legacy reports these IRETURNs (after ICONST_0) as FALSE_RETURNS; the rebuild makes them return true",
            ) {
                it.isBoolean && it.opcode == "ICONST_0"
            },
            newOnly(
                "D9d",
                "false-returns",
                "legacy reports these IRETURNs (after ICONST_1) as TRUE_RETURNS; the rebuild makes them return false",
            ) {
                it.isBoolean && it.opcode == "ICONST_1"
            },
            newOnly(
                "D10",
                "sealed-when",
                "legacy finds only the 2nd+ INSTANCEOF on one line; the rebuild has a site per when branch",
            ) {
                it.opcode in setOf("INSTANCEOF", "INVOKESTATIC", "TABLESWITCH", "LOOKUPSWITCH")
            },
            newOnly(
                "D11",
                "coroutine",
                "legacy never matches suspend calls (its descriptor test cannot hold) and mutates the " +
                    "COROUTINE_SUSPENDED marker, which breaks the state machine; the rebuild mutates the suspend calls",
            ) {
                it.isSuspend
            },
            legacyOnly(
                "D11b",
                "coroutine",
                "legacy mutates the COROUTINE_SUSPENDED marker call and the create(Continuation) call of a suspend " +
                    "lambda (its descriptor ends in Continuation); both are compiler plumbing the rebuild skips",
            ) {
                it.isSuspend &&
                    (
                        (it.opcode == "INVOKEVIRTUAL" && it.inMethod("invoke")) ||
                            (it.opcode == "INVOKESTATIC" && it.inMethod("invokeSuspend"))
                    )
            },
            legacyOnly(
                "D14a",
                "non-void-method-calls",
                "legacy mutates data class copy and copy\$default calls; the rebuild leaves them to data-class-copy",
            ) {
                it.inClass("Pair2", "ParityIdioms", "Wrapper") &&
                    (
                        (it.opcode == "INVOKESTATIC" && it.inMethod("withA", "bump", "rename", "bumped", "renamed")) ||
                            (it.opcode == "INVOKEVIRTUAL" && it.inMethod("both", "twice", "swap", "full"))
                    )
            },
            legacyOnly(
                "D14b",
                "non-void-method-calls",
                "legacy mutates Intrinsics.areEqual (object branch of a when, generated equals); the rebuild skips it",
            ) {
                it.opcode == "INVOKESTATIC" &&
                    (
                        (it.inClass("Pair2") && it.inMethod("equals")) ||
                            (it.inClass("ParityIdioms") && it.inMethod("evalInline")) ||
                            (it.inClass("ParityIdioms") && it.inMethod("eval") && it.line == 44)
                    )
            },
            newOnly(
                "D15",
                "data-class-copy",
                "legacy fires only after a componentN method was seen in the calling class, so it misses copy calls " +
                    "in a data class (its componentN follow the user methods) and in top-level functions; " +
                    "the rebuild fires on all",
            ) {
                it.inClass("Pair2", "ParityIdioms") &&
                    (
                        (it.opcode == "INVOKESTATIC" && it.inMethod("withA", "bump", "rename")) ||
                            (it.opcode == "INVOKEVIRTUAL" && it.inMethod("both", "twice", "swap"))
                    )
            },
            legacyOnly(
                "D12a",
                "constructor-calls",
                "legacy mutates constructions in inlined stdlib bodies (map, filter); the rebuild skips them",
            ) {
                it.inInlinedBody
            },
            legacyOnly(
                "D12b",
                "negate-conditionals",
                "legacy negates loop conditions in inlined stdlib bodies (map, filter, fold); the rebuild skips them",
            ) {
                it.inInlinedBody
            },
            legacyOnly(
                "D12c",
                "non-void-method-calls",
                "legacy mutates iterator, boxing and helper calls inside inlined stdlib bodies; the rebuild skips them",
            ) {
                it.inInlinedBody
            },
            legacyOnly(
                "D13",
                "non-void-method-calls",
                "legacy mutates every compiler copy of a finally body on its own; the rebuild has one site per body " +
                    "and changes all its copies together",
            ) {
                it.inMethod("guarded") && it.opcode == "INVOKEVIRTUAL" && it.line == 53
            },
            legacyOnly(
                "D16a",
                "arithmetic",
                GENERATED_DATA_MEMBER_REASON,
            ) {
                it.inGeneratedDataMember
            },
            legacyOnly(
                "D16b",
                "negate-conditionals",
                GENERATED_DATA_MEMBER_REASON,
            ) {
                it.inGeneratedDataMember
            },
            legacyOnly(
                "D16c",
                "non-void-method-calls",
                GENERATED_DATA_MEMBER_REASON,
            ) {
                it.inGeneratedDataMember
            },
            legacyOnly(
                "D16d",
                "null-returns",
                GENERATED_DATA_MEMBER_REASON,
            ) {
                it.inGeneratedDataMember
            },
            legacyOnly(
                "D16e",
                "null-safety",
                GENERATED_DATA_MEMBER_REASON,
            ) {
                it.inGeneratedDataMember
            },
            legacyOnly(
                "D16f",
                "return-vals",
                GENERATED_DATA_MEMBER_REASON,
            ) {
                it.inGeneratedDataMember
            },
            legacyOnly("D17a", "arithmetic", STATE_MACHINE_REASON) { it.inStateMachine },
            legacyOnly("D17b", "negate-conditionals", STATE_MACHINE_REASON) { it.inStateMachine },
            legacyOnly("D18a", "empty-returns", ENUM_MEMBER_REASON) { it.inGeneratedEnumMember },
            legacyOnly("D18b", "non-void-method-calls", ENUM_MEMBER_REASON) { it.inGeneratedEnumMember },
            legacyOnly("D18c", "null-returns", ENUM_MEMBER_REASON) { it.inGeneratedEnumMember },
        )
}
