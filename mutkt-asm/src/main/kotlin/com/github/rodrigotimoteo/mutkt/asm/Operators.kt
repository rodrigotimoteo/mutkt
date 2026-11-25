package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.asm.operators.ArithmeticOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.ConditionalsBoundaryOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.ConstructorCallsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.CoroutineOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.DataClassCopyOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.EmptyReturnsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.FalseReturnsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.IncrementsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.NegateConditionalsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.NonVoidMethodCallsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.NullReturnsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.NullSafetyOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.ReturnValsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.SealedWhenOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.TrueReturnsOperator
import com.github.rodrigotimoteo.mutkt.asm.operators.VoidMethodCallsOperator

/** Every operator, in the order they are applied and reported. */
internal object Operators {
    val all: List<Operator> =
        listOf(
            NegateConditionalsOperator,
            ArithmeticOperator,
            ConditionalsBoundaryOperator,
            NullReturnsOperator,
            ReturnValsOperator,
            EmptyReturnsOperator,
            ConstructorCallsOperator,
            IncrementsOperator,
            TrueReturnsOperator,
            FalseReturnsOperator,
            VoidMethodCallsOperator,
            NonVoidMethodCallsOperator,
            SealedWhenOperator,
            NullSafetyOperator,
            DataClassCopyOperator,
            CoroutineOperator,
        )

    /** The operators named by [ids], in application order; no ids means all. */
    fun byId(ids: Set<String>): List<Operator> {
        val valid = all.map { it.id.value }
        val unknown = ids.filterNot(valid::contains)
        require(unknown.isEmpty()) {
            "Unknown operator ${unknown.joinToString { "'$it'" }}; valid operators: ${valid.joinToString { "'$it'" }}"
        }
        return if (ids.isEmpty()) all else all.filter { it.id.value in ids }
    }
}
