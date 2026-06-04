package com.github.rodrigotimoteo.mutation.mutator

/**
 * Information about a mutation point discovered during bytecode scanning.
 */
data class MutationInfo(
    val operator: MutationOperator,
    val className: String,
    val methodName: String,
    val methodDescriptor: String,
    val lineNumber: Int,
    val description: String,
    val originalOpcode: Int,
    val mutatedOpcode: Int,
    val metadata: Map<String, String> = emptyMap()
)