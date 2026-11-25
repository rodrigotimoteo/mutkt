package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.ICONST_1

/** Replaces the value returned by a boolean method with true. */
internal object TrueReturnsOperator : ConstantBooleanReturn() {
    override val id = OperatorId("true-returns")
    override val constant = ICONST_1
}
