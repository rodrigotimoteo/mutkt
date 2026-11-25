package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.ICONST_0

/** Replaces the value returned by a boolean method with false. */
internal object FalseReturnsOperator : ConstantBooleanReturn() {
    override val id = OperatorId("false-returns")
    override val constant = ICONST_0
}
