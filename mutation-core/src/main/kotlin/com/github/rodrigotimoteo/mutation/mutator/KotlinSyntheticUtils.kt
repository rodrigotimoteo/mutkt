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
