package com.github.rodrigotimoteo.mutkt.asm

import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.SWAP
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode

private fun AbstractInsnNode.previousReal(): AbstractInsnNode? =
    generateSequence(previous) { it.previous }.firstOrNull { it.opcode >= 0 }

/**
 * True for the `ordinal()` call of an enum `when`: `GETSTATIC Caller$WhenMappings.$EnumSwitchMapping$n; SWAP;
 * INVOKEVIRTUAL ordinal`. Only the compiler emits it, and only for an enum subject; the SWAP tells it from
 * the user's own `table[e.ordinal]`.
 */
internal fun isMappedOrdinal(ordinal: AbstractInsnNode): Boolean {
    val table = ordinal.previousReal()?.takeIf { it.opcode == SWAP }?.previousReal() as? FieldInsnNode
    return table?.opcode == GETSTATIC && table.owner.endsWith("\$WhenMappings")
}
