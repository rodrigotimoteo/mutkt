package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.IFEQ
import org.objectweb.asm.Opcodes.IF_ACMPNE
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.MethodNode

private val NAMES =
    listOf(
        "ifeq",
        "ifne",
        "iflt",
        "ifge",
        "ifgt",
        "ifle",
        "if_icmpeq",
        "if_icmpne",
        "if_icmplt",
        "if_icmpge",
        "if_icmpgt",
        "if_icmple",
        "if_acmpeq",
        "if_acmpne",
    )

// The JVM numbers each condition and its negation next to each other, starting at IFEQ: IFEQ/IFNE, IFLT/IFGE, ...
private fun negated(opcode: Int) = if ((opcode - IFEQ) % 2 == 0) opcode + 1 else opcode - 1

private fun name(opcode: Int) = NAMES[opcode - IFEQ]

/** Replaces a conditional jump with its logical negation. IFNULL/IFNONNULL are deliberately not sites. */
internal object NegateConditionalsOperator : Operator {
    override val id = OperatorId("negate-conditionals")

    override fun sites(method: MethodNode): List<Site> =
        method.instructions
            .filterIsInstance<JumpInsnNode>()
            .filter { it.opcode in IFEQ..IF_ACMPNE }
            .map { Site(it, "${name(it.opcode)} -> ${name(negated(it.opcode))}") }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) {
        val jump = site.instruction as JumpInsnNode
        jump.opcode = negated(jump.opcode)
    }
}
