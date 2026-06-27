package com.github.rodrigotimoteo.mutation.mutator

/**
 * Check if a Kotlin method is a compiler-generated default/synthetic
 * bridge that the mutation engine should skip.
 *
 * The name "synthetic" is broad (in Kotlin/Java bytecode, "synthetic"
 * is a flag bit, not a name pattern), but here the matcher is precise:
 * it targets the named set of `$default` / `$serializer` helpers that
 * the Kotlin compiler emits for data class `copy`, data class
 * `componentN`, the empty lambda serializer, and the synthetic
 * `toString`/`hashCode`/`equals` defaults. User methods that merely
 * share a name prefix (e.g. `getCompanion`, `invokeSuspend`,
 * `myCopy$default`) are NOT matched — only the exact synthetic
 * suffixes the compiler actually emits.
 *
 * Used by [MutationScanner] and [MutationApplier] to skip these
 * helpers: mutating them has no observable effect because the caller
 * site always supplies the default value, so any change would be
 * dead code that wastes a mutation slot.
 *
 * @param name The method name to test (without the class prefix).
 * @return `true` iff the name matches one of the precise synthetic
 *   patterns below.
 */
internal fun isKotlinSyntheticMethod(name: String): Boolean {
    return name == "copy\$default" ||
        name.startsWith("component") && name.endsWith("\$default") ||
        name.endsWith("\$serializer") ||
        name == "<init>\$default" ||
        name == "toString\$default" ||
        name == "hashCode\$default" ||
        name == "equals\$default"
}

/**
 * True for a Kotlin data class component accessor — a generated method named
 * `component<N>` where `<N>` is a positive integer (e.g. `component1`,
 * `component2`, ...). Used as a low-cost data-class marker because
 * `@kotlin.Metadata.d1` is exposed by ASM as `String[]` and the visitor
 * cannot byte-parse the protobuf flags. Real data classes auto-generate
 * one `componentN` per primary-constructor property; non-data classes
 * never emit these names.
 *
 * `component$N$default` synthetic bridges are NOT matched here — they're
 * already handled by [isKotlinSyntheticMethod].
 */
internal fun isKotlinComponentNMethod(name: String): Boolean {
    if (!name.startsWith("component")) return false
    val rest = name.removePrefix("component")
    if (rest.isEmpty()) return false
    var i = 0
    // Reject leading zeros (e.g. `component01`) — generated accessors never
    // have them. Also reject non-digit chars via the allDigit scan.
    if (rest[0] == '0') return false
    while (i < rest.length) {
        if (rest[i] !in '0'..'9') return false
        i++
    }
    return true
}
