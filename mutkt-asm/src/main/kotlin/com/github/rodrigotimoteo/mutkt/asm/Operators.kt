package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.asm.operators.ArithmeticOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.ConditionalsBoundaryOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.NegateConditionalsOperator

/** Every operator, in the order they are applied and reported. */
internal object Operators {
    val all: List<Operator> =
        listOf(
            NegateConditionalsOperator,
            ArithmeticOperator,
            ConditionalsBoundaryOperator,
        )
}
