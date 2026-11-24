package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.IFGE
import org.objectweb.asm.Opcodes.IFGT
import org.objectweb.asm.Opcodes.IFLE
import org.objectweb.asm.Opcodes.IFLT
import org.objectweb.asm.Opcodes.IF_ICMPGE
import org.objectweb.asm.Opcodes.IF_ICMPGT
import org.objectweb.asm.Opcodes.IF_ICMPLE
import org.objectweb.asm.Opcodes.IF_ICMPLT
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.MethodNode

private val NAMES =
    mapOf(
        IFGE to "ifge",
        IFGT to "ifgt",
        IFLE to "ifle",
        IFLT to "iflt",
        IF_ICMPGE to "if_icmpge",
        IF_ICMPGT to "if_icmpgt",
        IF_ICMPLE to "if_icmple",
        IF_ICMPLT to "if_icmplt",
    )

private val PAIRS = listOf(IFGE to IFGT, IFLE to IFLT, IF_ICMPGE to IF_ICMPGT, IF_ICMPLE to IF_ICMPLT)
private val SWAP = (PAIRS + PAIRS.map { (a, b) -> b to a }).toMap()

/**
 * Moves a relational boundary (`<` to `<=`, `>` to `>=` and back) by swapping the jump opcode.
 * Long and double comparisons are covered too: LCMP/DCMPx leave an int that an IFxx jump tests.
 * IF_ACMP* is not a site (reference identity has no boundary).
 */
internal object ConditionalsBoundaryOperator : Operator {
    override val id = OperatorId("conditionals-boundary")

    override fun sites(method: MethodNode): List<Site> =
        method.instructions
            .filterIsInstance<JumpInsnNode>()
            .filter { it.opcode in SWAP }
            .map { Site(it, "${NAMES.getValue(it.opcode)} -> ${NAMES.getValue(SWAP.getValue(it.opcode))}") }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) {
        val jump = site.instruction as JumpInsnNode
        jump.opcode = SWAP.getValue(jump.opcode)
    }
}
