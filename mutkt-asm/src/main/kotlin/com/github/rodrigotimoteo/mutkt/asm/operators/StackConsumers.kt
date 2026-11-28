package com.github.rodrigotimoteo.mutkt.asm.operators

import org.objectweb.asm.Opcodes.ACONST_NULL
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.ILOAD
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.LDC
import org.objectweb.asm.Opcodes.NEW
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode

/** Labels control can reach other than by falling through: jump and switch targets and exception handlers. */
internal fun jumpTargets(method: MethodNode): Set<LabelNode> =
    method.instructions
        .flatMap {
            when (it) {
                is JumpInsnNode -> listOf(it.label)
                is TableSwitchInsnNode -> it.labels + it.dflt
                is LookupSwitchInsnNode -> it.labels + it.dflt
                else -> emptyList()
            }
        }.toSet() + method.tryCatchBlocks.map { it.handler }

/** The [call] that pops a value and the value's index among the call's arguments (-1 for the receiver). */
internal class Consumer(
    val call: AbstractInsnNode,
    val argument: Int,
)

/**
 * The call that pops the value [pushed] leaves on the stack, followed along straight-line code: values pushed above
 * it are counted and calls pop theirs. Null at one of the [targets], a branch, a store, a `DUP` of the value or any
 * other instruction.
 */
internal fun consumerOf(
    pushed: AbstractInsnNode,
    targets: Set<LabelNode>,
): Consumer? {
    var above = 0
    val stop =
        generateSequence(pushed.next) { it.next }
            .takeWhile { it !in targets }
            .filter { it.opcode >= 0 }
            .firstOrNull { insn ->
                val effect = stackEffect(insn)
                val stops = effect == null || effect.first > above
                if (effect != null && !stops) above += effect.second - effect.first
                stops
            }
    val call = stop?.takeIf { it is MethodInsnNode || it is InvokeDynamicInsnNode } ?: return null
    val popped = checkNotNull(stackEffect(call)).first
    return Consumer(call, popped - 1 - above - receiverOf(call))
}

private fun receiverOf(insn: AbstractInsnNode) = if (insn is MethodInsnNode && insn.opcode != INVOKESTATIC) 1 else 0

// Values popped and pushed: a call pops its arguments and receiver; null for instructions not followed.
private fun stackEffect(insn: AbstractInsnNode): Pair<Int, Int>? {
    val desc = (insn as? MethodInsnNode)?.desc ?: (insn as? InvokeDynamicInsnNode)?.desc
    val opcode = insn.opcode
    return when {
        desc != null ->
            Type.getArgumentTypes(desc).size + receiverOf(insn) to
                if (Type.getReturnType(desc) == Type.VOID_TYPE) 0 else 1
        opcode == DUP -> 1 to 2
        opcode in ACONST_NULL..LDC || opcode in ILOAD..ALOAD || opcode == GETSTATIC || opcode == NEW -> 0 to 1
        else -> null
    }
}
