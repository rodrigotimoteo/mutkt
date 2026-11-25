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

private fun Row.inMethod(vararg names: String) = names.any { method.startsWith("$it(") }

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

// Legacy operators without a new counterpart (DATA_CLASS_COPY) are not
// deviations: they are listed as not implemented by the legacy table.
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
                "legacy mutates a directly thrown construction (throw X()); the rebuild skips it as noise",
            ) {
                it.inMethod("area", "hex", "chain")
            },
            legacyOnly(
                "D7",
                "void-method-calls",
                "legacy removes Intrinsics.checkNotNull* and ResultKt.throwOnFailure; the rebuild skips them",
            ) {
                // Parameter checks run before the first line number; the other rows are plumbing in named methods.
                it.opcode == "INVOKESTATIC" &&
                    (it.line == -1 || it.isSuspend || it.inMethod("javaEmptyList", "javaEmptyCollection"))
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
                "legacy mutates coroutine plumbing (suspend calls, COROUTINE_SUSPENDED, Boxing); the rebuild skips it",
            ) {
                it.opcode == "INVOKESTATIC" && it.isSuspend
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
                "D11",
                "coroutine",
                "legacy never matches suspend calls (its descriptor test cannot hold) and mutates the " +
                    "COROUTINE_SUSPENDED marker, which breaks the state machine; the rebuild mutates the suspend calls",
            ) {
                it.inMethod("chain")
            },
            newOnly(
                "D10",
                "sealed-when",
                "legacy finds no site (needs two INSTANCEOF on one line); the rebuild has a site per when branch",
            ) {
                it.opcode in setOf("INSTANCEOF", "INVOKESTATIC", "TABLESWITCH", "LOOKUPSWITCH")
            },
        )
}
