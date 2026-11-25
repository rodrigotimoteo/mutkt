package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.SiteFilter
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.GOTO
import org.objectweb.asm.Opcodes.IFNE
import org.objectweb.asm.Opcodes.IFNULL
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.VarInsnNode

/**
 * A `when` on a String with several branches compiles to `switch (s.hashCode())` whose every case runs a chain
 * of `s.equals("constant")`. The hashCode call and the equals calls are plumbing; the branch jumps are not.
 * An equals call is a guard when its receiver is the local the hash was taken from, its argument is a constant,
 * and its result is tested by a jump that is followed by the next guard or by the jump to the switch default.
 * A user-written switch on `hashCode()` whose cases compare another receiver, return the result or branch
 * on it differently is not matched.
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
        val subject = subjectOf(hash)
        val chains = subject?.let { local -> targets(switch).map { equalsChain(it, local, defaultOf(switch)) } }
        return if (chains != null && chains.all { it.isNotEmpty() }) listOf(hash) + chains.flatten() else emptyList()
    }

    private fun targets(switch: AbstractInsnNode): List<LabelNode> =
        when (switch) {
            is LookupSwitchInsnNode -> switch.labels
            is TableSwitchInsnNode -> switch.labels
            else -> emptyList()
        }

    private fun defaultOf(switch: AbstractInsnNode): LabelNode? =
        when (switch) {
            is LookupSwitchInsnNode -> switch.dflt
            is TableSwitchInsnNode -> switch.dflt
            else -> null
        }

    // The subject is a stored local; a nullable subject is tested first (`ALOAD; DUP; IFNULL`).
    private fun subjectOf(hash: AbstractInsnNode): Int? {
        val receiver =
            generateSequence(realBefore(hash)) { realBefore(it) }.firstOrNull { it.opcode !in setOf(IFNULL, DUP) }
        return (receiver as? VarInsnNode)?.takeIf { it.opcode == ALOAD }?.`var`
    }

    // The chain ends in the GOTO to the default branch.
    private fun equalsChain(
        target: LabelNode,
        subject: Int,
        default: LabelNode?,
    ): List<AbstractInsnNode> =
        generateSequence<AbstractInsnNode>(target) { it.next }
            .takeWhile { it.opcode != GOTO }
            .filter { it.isString("equals", "(Ljava/lang/Object;)Z") && isGuard(it, subject, default) }
            .toList()

    private fun isGuard(
        equals: AbstractInsnNode,
        subject: Int,
        default: LabelNode?,
    ): Boolean {
        val constant = realBefore(equals)?.takeIf { it is LdcInsnNode && it.cst is String }
        val receiver = constant?.let(::realBefore) as? VarInsnNode
        val jump = realAfter(equals)?.takeIf { it.opcode == IFNE }
        val next = jump?.let(::realAfter)
        val onSubject = receiver?.opcode == ALOAD && receiver.`var` == subject
        return onSubject && (next.isGoto(default) || next.isLoad(subject))
    }

    private fun AbstractInsnNode?.isGoto(label: LabelNode?) =
        this is JumpInsnNode && opcode == GOTO && this.label === label

    private fun AbstractInsnNode?.isLoad(local: Int) = this is VarInsnNode && opcode == ALOAD && `var` == local

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
