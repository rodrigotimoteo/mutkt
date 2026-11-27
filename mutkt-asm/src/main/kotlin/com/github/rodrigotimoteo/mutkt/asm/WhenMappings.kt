package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.asm.filters.realBefore
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.IALOAD
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Opcodes.SWAP
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode

/**
 * True for the `ordinal()` call of an enum `when`: `GETSTATIC Caller$WhenMappings.$EnumSwitchMapping$n; SWAP;
 * INVOKEVIRTUAL ordinal`. Only the compiler emits it, and only for an enum subject; the SWAP tells it from
 * the user's own `table[e.ordinal]`.
 */
internal fun isMappedOrdinal(ordinal: AbstractInsnNode): Boolean {
    val table = realBefore(ordinal)?.takeIf { it.opcode == SWAP }?.let(::realBefore) as? FieldInsnNode
    return table?.opcode == GETSTATIC && table.owner.endsWith("\$WhenMappings")
}

/**
 * The `ordinal()` call a [switch] selects on, as `mapping[e.ordinal()]` (the `$WhenMappings` array Kotlin
 * builds) or `e.ordinal()` itself; null for any other selector, such as an Int or a String `hashCode()`.
 */
internal fun ordinalOf(switch: AbstractInsnNode): MethodInsnNode? {
    val selector = realBefore(switch)?.let { if (it.opcode == IALOAD) realBefore(it) else it }
    return (selector as? MethodInsnNode)?.takeIf {
        it.opcode == INVOKEVIRTUAL && it.name == "ordinal" && it.desc == "()I"
    }
}
