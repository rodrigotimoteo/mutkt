package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.DREM
import org.objectweb.asm.Opcodes.IADD
import org.objectweb.asm.Opcodes.IDIV
import org.objectweb.asm.Opcodes.IMUL
import org.objectweb.asm.Opcodes.IREM
import org.objectweb.asm.Opcodes.ISUB
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodNode

// The binary arithmetic opcodes are laid out as int, long, float, double for each of add, sub, mul, div, rem,
// so the int opcode picks the operation and (opcode - IADD) % 4 the operand type. Every swap keeps the stack
// effect (two operands in, one result out, same width), so no other instruction needs to change.
private const val TYPES = "ilfd"
private val SWAPS = mapOf(IADD to ISUB, ISUB to IADD, IMUL to IDIV, IDIV to IMUL, IREM to IMUL)
private val NAMES = mapOf(IADD to "add", ISUB to "sub", IMUL to "mul", IDIV to "div", IREM to "rem")

/** Swaps add with sub, mul with div and rem with mul for int, long, float and double; never touches IINC or NEG. */
internal object ArithmeticOperator : Operator {
    override val id = OperatorId("arithmetic")

    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> =
        method.instructions
            .filter { it.opcode in IADD..DREM }
            .map { Site(it, describe(it.opcode)) }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) = method.instructions.set(site.instruction, InsnNode(mutated(site.instruction.opcode)))

    private fun baseOf(opcode: Int) = IADD + (opcode - IADD) / TYPES.length * TYPES.length

    private fun mutated(opcode: Int) = checkNotNull(SWAPS[baseOf(opcode)]) + (opcode - baseOf(opcode))

    private fun describe(opcode: Int): String {
        val type = TYPES[(opcode - IADD) % TYPES.length]
        return "$type${NAMES.getValue(baseOf(opcode))} -> $type${NAMES.getValue(baseOf(mutated(opcode)))}"
    }
}
