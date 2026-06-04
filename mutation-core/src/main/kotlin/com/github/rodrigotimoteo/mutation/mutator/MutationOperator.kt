package com.github.rodrigotimoteo.mutation.mutator

/**
 * Mutation operators supported by the engine.
 * MVP operators cover the most common and valuable mutations.
 */
enum class MutationOperator(val operatorName: String, val description: String) {
    // Conditional boundary mutations
    CONDITIONALS_BOUNDARY(
        "CONDITIONALS_BOUNDARY",
        "Mutates conditional boundaries: >= -> >, <= -> <, > -> >=, < -> <="
    ),
    NEGATE_CONDITIONALS(
        "NEGATE_CONDITIONALS",
        "Negates conditional operators: == -> !=, != -> ==, > -> <=, < -> >="
    ),

    // Arithmetic mutations
    ARITHMETIC(
        "ARITHMETIC",
        "Mutates arithmetic operators: + -> -, - -> +, * -> /, / -> *, % -> *"
    ),

    // Return value mutations
    RETURN_VALS(
        "RETURN_VALS",
        "Replaces return values with constants: 0, 1, -1, true, false, empty string"
    ),
    NULL_RETURNS(
        "NULL_RETURNS",
        "Replaces reference type returns with null"
    ),
    EMPTY_RETURNS(
        "EMPTY_RETURNS",
        "Replaces collection/array returns with empty instances"
    ),

    // Boolean mutations
    INVERT_NEGS(
        "INVERT_NEGS",
        "Inverts boolean negation: !x -> x, x -> !x"
    );

    companion object {
        val MVP_OPERATORS = setOf(
            CONDITIONALS_BOUNDARY,
            NEGATE_CONDITIONALS,
            ARITHMETIC,
            RETURN_VALS,
            NULL_RETURNS,
            EMPTY_RETURNS,
            INVERT_NEGS
        )

        fun fromName(name: String): MutationOperator? {
            return values().firstOrNull { it.operatorName == name }
        }
    }
}