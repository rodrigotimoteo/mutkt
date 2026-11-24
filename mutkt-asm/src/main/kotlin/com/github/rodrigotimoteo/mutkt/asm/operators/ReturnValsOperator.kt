package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.DCONST_0
import org.objectweb.asm.Opcodes.DRETURN
import org.objectweb.asm.Opcodes.FCONST_0
import org.objectweb.asm.Opcodes.FRETURN
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.IRETURN
import org.objectweb.asm.Opcodes.LCONST_0
import org.objectweb.asm.Opcodes.LRETURN
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.Opcodes.POP2
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodNode

private data class Zeroing(
    val pop: Int,
    val zero: Int,
    val name: String,
)

private val ZEROING =
    mapOf(
        IRETURN to Zeroing(POP, ICONST_0, "ireturn"),
        LRETURN to Zeroing(POP2, LCONST_0, "lreturn"),
        FRETURN to Zeroing(POP, FCONST_0, "freturn"),
        DRETURN to Zeroing(POP2, DCONST_0, "dreturn"),
    )

/** Replaces the value returned by an int, long, float or double method with zero. */
internal object ReturnValsOperator : Operator {
    override val id = OperatorId("return-vals")

    override fun sites(method: MethodNode): List<Site> =
        // IRETURN also returns booleans, which are left to the boolean-return operators.
        if (method.desc.endsWith(")Z")) {
            emptyList()
        } else {
            method.instructions
                .filter { it.opcode in ZEROING && it.previous?.opcode != ZEROING.getValue(it.opcode).zero }
                .map { Site(it, "${ZEROING.getValue(it.opcode).name} -> 0") }
        }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) {
        val zeroing = ZEROING.getValue(site.instruction.opcode)
        method.instructions.insertBefore(site.instruction, InsnNode(zeroing.pop))
        method.instructions.insertBefore(site.instruction, InsnNode(zeroing.zero))
    }
}
