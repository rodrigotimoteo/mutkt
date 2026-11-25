package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.SiteFilter
import com.github.rodrigotimoteo.mutkt.asm.parse
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.util.Printer

private fun describe(insn: AbstractInsnNode): String {
    val call = insn as? MethodInsnNode ?: return Printer.OPCODES[insn.opcode]
    return "${Printer.OPCODES[insn.opcode]} ${call.owner.substringAfterLast('/')}.${call.name}"
}

/** What this filter marks as noise in a method, in instruction order, as "OPCODE" or "OPCODE Owner.name". */
internal fun SiteFilter.noiseIn(
    classes: Map<String, ByteArray>,
    className: String,
    method: String,
): List<String> {
    val node = parse(classes.getValue(className))
    val target = node.methods.single { it.name == method }
    return noise(
        node.name,
        target,
    ).filter { it.opcode >= 0 }.sortedBy { target.instructions.indexOf(it) }.map(::describe)
}
