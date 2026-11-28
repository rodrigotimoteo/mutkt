package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.ANEWARRAY
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.NEWARRAY
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.TypeInsnNode

/** Replaces a returned array, List, Set, Map or Collection with an empty one. */
internal object EmptyReturnsOperator : Operator {
    override val id = OperatorId("empty-returns")

    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> =
        if (hasEmptyValue(Type.getReturnType(method.desc).descriptor)) {
            val targets = jumpTargets(method)
            method.instructions
                .filter { it.opcode == ARETURN }
                .filterNot { returnsEmptyAlready(it, targets) }
                .map { Site(it, "areturn -> empty") }
        } else {
            emptyList()
        }

    // The original ARETURN stays and returns the pushed empty value.
    override fun apply(
        method: MethodNode,
        site: Site,
    ) = method.instructions.insertBefore(
        site.instruction,
        InsnList().apply {
            add(InsnNode(POP))
            add(emptyValue(Type.getReturnType(method.desc).descriptor))
        },
    )

    private fun jumpTargets(method: MethodNode): Set<LabelNode> =
        buildSet {
            method.instructions.forEach {
                when (it) {
                    is JumpInsnNode -> add(it.label)
                    is TableSwitchInsnNode -> addAll(it.labels + it.dflt)
                    is LookupSwitchInsnNode -> addAll(it.labels + it.dflt)
                }
            }
            method.tryCatchBlocks.forEach { add(it.handler) }
        }

    // Line-number labels sit between the value and the return; a jump target or frame there is a merge point,
    // where the value may not be empty, so the site stays.
    private fun returnsEmptyAlready(
        ret: AbstractInsnNode,
        targets: Set<LabelNode>,
    ): Boolean {
        var previous = ret.previous
        while (previous is LineNumberNode ||
            (previous is LabelNode && previous !in targets)
        ) {
            previous = previous.previous
        }
        return when (previous) {
            is MethodInsnNode -> previous.isEmptyFactoryCall()
            is TypeInsnNode -> previous.opcode == ANEWARRAY && previous.previous?.opcode == ICONST_0
            is IntInsnNode -> previous.opcode == NEWARRAY && previous.previous?.opcode == ICONST_0
            else -> false
        }
    }
}
