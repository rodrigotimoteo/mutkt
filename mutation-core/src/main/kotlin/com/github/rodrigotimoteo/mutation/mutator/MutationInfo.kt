package com.github.rodrigotimoteo.mutation.mutator

/**
 * Information about a mutation point discovered during bytecode scanning.
 *
 * @property operator The mutation operator that produced this point.
 * @property className Fully qualified class name (dotted form).
 * @property methodName Simple method name containing the mutation point.
 * @property methodDescriptor JVM method descriptor (return + parameter types).
 * @property lineNumber Source line where the mutation point was found.
 * @property description Human-readable description of the mutation.
 * @property originalOpcode Original JVM opcode at the mutation site.
 * @property mutatedOpcode Opcode the original is rewritten to.
 * @property metadata Free-form per-mutation attributes (e.g. occurrence index).
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
    val metadata: Map<String, String> = emptyMap(),
)
