package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.SiteFilter
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.IALOAD
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Opcodes.SWAP
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * A `when` on an enum indexes the synthetic `$WhenMappings` table with the subject's ordinal:
 * `GETSTATIC Caller$WhenMappings.$EnumSwitchMapping$n; SWAP; INVOKEVIRTUAL ordinal; IALOAD`. That ordinal call is
 * plumbing. The SWAP tells it from the user's own `table[e.ordinal]`. The mapping class holds only a static
 * initializer, which the skip rules already ignore.
 */
internal object WhenMappingsFilter : SiteFilter {
    override fun noise(
        className: String,
        method: MethodNode,
    ): Set<AbstractInsnNode> =
        method.instructions
            .filterIsInstance<MethodInsnNode>()
            .filter { it.opcode == INVOKEVIRTUAL && it.name == "ordinal" && it.desc == "()I" }
            .filter { realAfter(it)?.opcode == IALOAD && isMappingLookup(it) }
            .toSet()

    private fun isMappingLookup(ordinal: AbstractInsnNode): Boolean {
        val swap = realBefore(ordinal)?.takeIf { it.opcode == SWAP }
        val table = swap?.let(::realBefore) as? FieldInsnNode
        return table?.opcode == GETSTATIC && table.owner.endsWith("\$WhenMappings")
    }

    private fun realAfter(insn: AbstractInsnNode): AbstractInsnNode? =
        generateSequence(insn.next) { it.next }.firstOrNull { it.opcode >= 0 }
}
