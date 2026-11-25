package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodNode

/** Replaces every IADD with ISUB; exists only to exercise the framework before real operators land. */
internal object ToyOperator : Operator {
    override val id = OperatorId("toy-add-to-sub")

    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> =
        method.instructions
            .filter { it.opcode == Opcodes.IADD }
            .map { Site(it, "iadd -> isub") }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) = method.instructions.set(site.instruction, InsnNode(Opcodes.ISUB))
}
