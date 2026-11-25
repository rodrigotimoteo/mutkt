package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.IINC
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.MethodNode

// IINC holds a signed 16-bit increment (wide form), so the most negative value has no positive counterpart.
private val INCREMENT_RANGE = Short.MIN_VALUE + 1..Short.MAX_VALUE

/** Flips the sign of a local variable increment: `i++` becomes `i--` and `i -= 2` becomes `i += 2`. */
internal object IncrementsOperator : Operator {
    override val id = OperatorId("increments")

    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> =
        method.instructions
            .filter { it.opcode == IINC }
            .map { it as IincInsnNode }
            .filter { it.incr != 0 && it.incr in INCREMENT_RANGE }
            .map { Site(it, if (it.incr > 0) "iinc +n -> -n" else "iinc -n -> +n") }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) {
        (site.instruction as IincInsnNode).incr *= -1
    }
}
