package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.asm.operators.ArithmeticOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.ConditionalsBoundaryOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.ConstructorCallsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.CoroutineOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.DataClassCopyOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.EmptyReturnsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.ExtremeOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.FalseReturnsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.IncrementsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.NegateConditionalsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.NonVoidMethodCallsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.NullReturnsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.NullSafetyOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.RemoveNegationOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.ReturnValsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.SealedWhenOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.StdlibSwapOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.StringsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.TrueReturnsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.VoidMethodCallsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.WhenSwitchOperator

internal object Operators {
    /** Enabled when no ids are given; the other operators are opt-in until proven on real projects. */
    val defaults: List<Operator> =
        listOf(
            NegateConditionalsOperator,
            ArithmeticOperator,
            ConditionalsBoundaryOperator,
            NullReturnsOperator,
            ReturnValsOperator,
            EmptyReturnsOperator,
            TrueReturnsOperator,
            FalseReturnsOperator,
        )

    private val optIn: List<Operator> =
        listOf(
            ConstructorCallsOperator,
            IncrementsOperator,
            VoidMethodCallsOperator,
            NonVoidMethodCallsOperator,
            SealedWhenOperator,
            NullSafetyOperator,
            DataClassCopyOperator,
            CoroutineOperator,
            WhenSwitchOperator,
            RemoveNegationOperator,
            StdlibSwapOperator,
            ExtremeOperator,
            StringsOperator,
        )

    /** Every operator, in the order they are applied and reported. */
    val all: List<Operator> = defaults + optIn

    /** The operators named by [ids], in application order; no ids means [defaults]. */
    fun byId(ids: Set<String>): List<Operator> {
        val valid = all.map { it.id.value }
        val unknown = ids.filterNot(valid::contains)
        require(unknown.isEmpty()) {
            "Unknown operator ${unknown.joinToString { "'$it'" }}; valid operators: ${valid.joinToString { "'$it'" }}"
        }
        return if (ids.isEmpty()) defaults else all.filter { it.id.value in ids }
    }
}
