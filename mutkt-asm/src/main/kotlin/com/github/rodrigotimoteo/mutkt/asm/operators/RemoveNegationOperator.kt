package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.DNEG
import org.objectweb.asm.Opcodes.INEG
import org.objectweb.asm.tree.MethodNode

// INEG, LNEG, FNEG, DNEG are consecutive and each pops and pushes one value of its own width, so dropping one
// leaves the stack unchanged.
private const val TYPES = "ilfd"

/** Drops a unary negation: `-x` becomes `x` for int, long, float and double. */
internal object RemoveNegationOperator : Operator {
    override val id = OperatorId("remove-negation")

    // In `-(-x)` both negations would give the same mutant, so the second one is not a site.
    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> =
        method.instructions
            .filter { it.opcode in INEG..DNEG && it.previous?.opcode != it.opcode }
            .map { Site(it, "${TYPES[it.opcode - INEG]}neg -> removed") }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) = method.instructions.remove(site.instruction)
}
