package com.github.rodrigotimoteo.mutation.model

import com.github.rodrigotimoteo.mutation.mutator.MutationOperator

/**
 * Represents a specific mutation (a change in the source code).
 *
 * Each mutation is identified by a unique ID and contains information
 * about the original and mutated bytecode, the operator applied,
 * and the location in the source code.
 *
 * @property id Unique identifier for this mutation
 * @property className Fully qualified class name containing the mutation
 * @property methodName Method name where the mutation was applied
 * @property methodDescriptor JVM method descriptor
 * @property operator The mutation operator applied
 * @property lineNumber Source line number (0 if unknown)
 * @property sourceFile Relative path to source file (null if unavailable)
 * @property sourceCode Source code snippet at mutation point (null if unavailable)
 * @property originalBytecode Original class bytecode before mutation. Engine
 *           tooling only — not consulted by equals/hashCode/copy; do not
 *           rely on it for identity checks. Left as a `val` (not `private
 *           val`) so the named-argument call sites in tests still compile.
 * @property mutatedBytecode Class bytecode after mutation. Engine tooling
 *           only — not consulted by equals/hashCode/copy.
 * @property description Human-readable description of the mutation
 */
data class Mutation(
    val id: String,
    val className: String,
    val methodName: String,
    val methodDescriptor: String,
    val operator: MutationOperator,
    val lineNumber: Int,
    val sourceFile: String? = null,
    val sourceCode: String? = null,
    val originalBytecode: ByteArray = ByteArray(0),
    val mutatedBytecode: ByteArray = ByteArray(0),
    val description: String,
) {
    /**
     * Recover the zero-based occurrence index for this mutation within
     * its (class, method, line) tuple. The [id] format encodes it as the
     * last `::` segment so the value can be recovered without storing an
     * extra constructor field (which would break data-class
     * `equals`/`hashCode` for older callers using positional construction).
     */
    fun getOccurrenceIndex(): Int = id.substringAfterLast("::").toIntOrNull() ?: 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Mutation) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "Mutation(id='$id', className='$className', methodName='$methodName', operator=$operator, lineNumber=$lineNumber)"
}
