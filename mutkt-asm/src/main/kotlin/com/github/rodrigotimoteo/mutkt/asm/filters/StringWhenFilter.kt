package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.SiteFilter
import org.objectweb.asm.Opcodes.GOTO
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode

/**
 * A `when` on a String with several branches compiles to `switch (s.hashCode())` whose every case runs a chain
 * of `s.equals("constant")`. The hashCode call and the equals calls are plumbing; the branch jumps are not.
 * A switch on a user-written `hashCode()` has cases that do not lead to `equals`, so it is not matched.
 */
internal object StringWhenFilter : SiteFilter {
    override fun noise(
        className: String,
        method: MethodNode,
    ): Set<AbstractInsnNode> =
        method.instructions
            .filter { it is LookupSwitchInsnNode || it is TableSwitchInsnNode }
            .flatMap { switch -> plumbing(switch) }
            .toSet()

    private fun plumbing(switch: AbstractInsnNode): List<AbstractInsnNode> {
        val hash = realBefore(switch)?.takeIf { it.isString("hashCode", "()I") } ?: return emptyList()
        val chains = targets(switch).map(::equalsChain)
        return if (chains.all { it.isNotEmpty() }) listOf(hash) + chains.flatten() else emptyList()
    }

    private fun targets(switch: AbstractInsnNode): List<LabelNode> =
        when (switch) {
            is LookupSwitchInsnNode -> switch.labels
            is TableSwitchInsnNode -> switch.labels
            else -> emptyList()
        }

    // The chain ends in the GOTO to the default branch.
    private fun equalsChain(target: LabelNode): List<AbstractInsnNode> =
        generateSequence<AbstractInsnNode>(target) { it.next }
            .takeWhile { it.opcode != GOTO }
            .filter { it.isString("equals", "(Ljava/lang/Object;)Z") && realBefore(it) is LdcInsnNode }
            .toList()

    private fun AbstractInsnNode.isString(
        name: String,
        desc: String,
    ): Boolean {
        val call = this as? MethodInsnNode ?: return false
        return call.opcode == INVOKEVIRTUAL &&
            call.owner == "java/lang/String" &&
            call.name == name &&
            call.desc == desc
    }
}
