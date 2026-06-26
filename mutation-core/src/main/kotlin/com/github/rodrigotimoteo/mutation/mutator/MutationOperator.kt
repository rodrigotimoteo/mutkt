package com.github.rodrigotimoteo.mutation.mutator

/**
 * Mutation operators supported by the engine.
 *
 * Each operator defines a specific type of bytecode transformation.
 * Operators are grouped into categories:
 * - **MVP**: Core operators for basic mutation testing
 * - **Kotlin**: Kotlin-specific mutations (data class, sealed when, etc.)
 * - **Quick Win**: Additional PITest-style operators
 *
 * Example:
 * ```kotlin
 * val operators = MutationOperator.MVP_OPERATORS + MutationOperator.KOTLIN_OPERATORS
 * val mutator = Mutator(operators)
 * ```
 */
enum class MutationOperator(val operatorName: String, val description: String) {
    // Conditional boundary mutations
    CONDITIONALS_BOUNDARY(
        "CONDITIONALS_BOUNDARY",
        "Mutates conditional boundaries: >= -> >, <= -> <, > -> >=, < -> <=",
    ),
    NEGATE_CONDITIONALS(
        "NEGATE_CONDITIONALS",
        "Negates conditional operators: == -> !=, != -> ==, > -> <=, < -> >=",
    ),

    // Arithmetic mutations
    ARITHMETIC(
        "ARITHMETIC",
        "Mutates arithmetic operators: + -> -, - -> +, * -> /, / -> *, % -> *",
    ),

    // Return value mutations
    RETURN_VALS(
        "RETURN_VALS",
        "Replaces return values with constants: 0, 1, -1, true, false, empty string",
    ),
    NULL_RETURNS(
        "NULL_RETURNS",
        "Replaces reference type returns with null",
    ),
    EMPTY_RETURNS(
        "EMPTY_RETURNS",
        "Replaces collection/array returns with empty instances",
    ),

    // Boolean mutations — covered by NEGATE_CONDITIONALS

    // Kotlin-specific mutations
    DATA_CLASS_COPY(
        "DATA_CLASS_COPY",
        "Mutates data class copy() parameters to default values",
    ),
    SEALED_WHEN(
        "SEALED_WHEN",
        "Removes or mutates sealed class when branches",
    ),
    NULL_SAFETY(
        "NULL_SAFETY",
        "Mutates null-safety operators: ?.let, ?:, !!",
    ),
    COROUTINE(
        "COROUTINE",
        "Mutates coroutine builders and suspend functions",
    ),

    // Additional PITest-style mutations
    VOID_METHOD_CALLS(
        "VOID_METHOD_CALLS",
        "Remove calls to void methods (side effects)",
    ),
    INCREMENTS(
        "INCREMENTS",
        "Replace ++ with -- and vice versa",
    ),
    TRUE_RETURNS(
        "TRUE_RETURNS",
        "Replace boolean returns with true",
    ),
    FALSE_RETURNS(
        "FALSE_RETURNS",
        "Replace boolean returns with false",
    ),
    CONSTRUCTOR_CALLS(
        "CONSTRUCTOR_CALLS",
        "Remove constructor calls (object creation)",
    ),
    NON_VOID_METHOD_CALLS(
        "NON_VOID_METHOD_CALLS",
        "Remove non-void method calls (ignore return value)",
    ),
    ;

    companion object {
        val MVP_OPERATORS =
            setOf(
                CONDITIONALS_BOUNDARY,
                NEGATE_CONDITIONALS,
                ARITHMETIC,
                RETURN_VALS,
                NULL_RETURNS,
                EMPTY_RETURNS,
            )

        val KOTLIN_OPERATORS =
            setOf(
                DATA_CLASS_COPY,
                SEALED_WHEN,
                NULL_SAFETY,
                COROUTINE,
            )

        val QUICK_WIN_OPERATORS =
            setOf(
                VOID_METHOD_CALLS,
                INCREMENTS,
                TRUE_RETURNS,
                FALSE_RETURNS,
                CONSTRUCTOR_CALLS,
                NON_VOID_METHOD_CALLS,
            )

        val ALL_OPERATORS = MVP_OPERATORS + KOTLIN_OPERATORS + QUICK_WIN_OPERATORS

        /**
         * Convert an operator name string to the enum, returns null if not recognized.
         *
         * @param name The string form (typically [operatorName]) to look up
         * @return Matching [MutationOperator] or `null` when no enum value has that name
         */
        fun fromName(name: String): MutationOperator? {
            return values().firstOrNull { it.operatorName == name }
        }
    }
}
