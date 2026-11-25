package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.ACONST_NULL
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * Replaces the result of a call to a data class `copy` (`copy(...)` or the default-argument bridge
 * `copy$default(...)`) by null, popping its arguments.
 *
 * The call site has no class context, so a copy call is recognised by shape only (see [isCopyCall]); a
 * non-data class with a same-shaped `copy` method is a site too.
 */
internal object DataClassCopyOperator : Operator {
    override val id = OperatorId("data-class-copy")

    override fun sites(method: MethodNode): List<Site> =
        method.instructions
            .filterIsInstance<MethodInsnNode>()
            .filter { it.isCopyCall() }
            .map { Site(it, it.describe("replace copy result with null:")) }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) = method.replaceCall(site.instruction as MethodInsnNode, InsnNode(ACONST_NULL))
}
