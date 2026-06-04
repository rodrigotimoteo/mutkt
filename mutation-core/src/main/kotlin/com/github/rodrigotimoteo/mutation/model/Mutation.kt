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
 * @property originalBytecode Original class bytecode before mutation
 * @property mutatedBytecode Class bytecode after mutation
 * @property description Human-readable description of the mutation
 */
data class Mutation(
    val id: String,
    val className: String,
    val methodName: String,
    val methodDescriptor: String,
    val operator: MutationOperator,
    val lineNumber: Int,
    val originalBytecode: ByteArray,
    val mutatedBytecode: ByteArray,
    val description: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Mutation) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "Mutation(id='$id', className='$className', methodName='$methodName', operator=$operator, lineNumber=$lineNumber)"
}
