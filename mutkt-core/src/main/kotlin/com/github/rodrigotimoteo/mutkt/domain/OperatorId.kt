package com.github.rodrigotimoteo.mutkt.domain

/** Stable name of a mutation operator, e.g. `negate-conditionals`. */
@JvmInline
public value class OperatorId(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "Operator id must not be blank" }
    }

    override fun toString(): String = value
}
