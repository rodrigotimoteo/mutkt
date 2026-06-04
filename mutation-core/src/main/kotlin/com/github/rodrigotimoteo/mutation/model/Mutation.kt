package com.github.rodrigotimoteo.mutation.model

import com.github.rodrigotimoteo.mutation.mutator.MutationOperator

/**
 * Represents a specific mutation (a change in the source code).
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
