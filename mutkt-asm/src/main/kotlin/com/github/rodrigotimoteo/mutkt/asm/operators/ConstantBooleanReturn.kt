package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import org.objectweb.asm.Opcodes.ICONST_1
import org.objectweb.asm.Opcodes.IRETURN
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * Replaces the boolean consumed by IRETURN with [constant] (ICONST_0 or ICONST_1), however it was computed.
 * Only the value on the stack matters, so constants, locals, calls and merged branches are all handled alike.
 */
internal abstract class ConstantBooleanReturn : Operator {
    abstract val constant: Int

    private val label get() = if (constant == ICONST_1) "true" else "false"

    override fun sites(method: MethodNode): List<Site> =
        if (method.desc.endsWith(")Z")) {
            method.instructions
                .filter { it.opcode == IRETURN }
                // previous is the adjacent node: a label or frame there marks a merge point, where the value
                // may not be the constant, so the site stays. Only a literal constant returned directly is equivalent.
                .filter { it.previous?.opcode != constant }
                .map { Site(it, "ireturn -> $label") }
        } else {
            emptyList()
        }

    // The original IRETURN stays and returns the pushed constant.
    override fun apply(
        method: MethodNode,
        site: Site,
    ) = method.instructions.insertBefore(
        site.instruction,
        InsnList().apply {
            add(InsnNode(POP))
            add(InsnNode(constant))
        },
    )
}
