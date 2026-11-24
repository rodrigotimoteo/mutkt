package com.github.rodrigotimoteo.mutkt.domain

/** Identifies one mutation site; the descriptor keeps overloads apart and the ordinal separates sites in a method. */
public data class MutantId(
    val operator: OperatorId,
    val className: String,
    val methodName: String,
    val methodDescriptor: String,
    val ordinal: Int,
) {
    init {
        require(ordinal >= 0) { "Ordinal must not be negative: $ordinal" }
    }

    /** Stable form `operator:class#method(descriptor):ordinal`. */
    override fun toString(): String = "$operator:$className#$methodName$methodDescriptor:$ordinal"
}
