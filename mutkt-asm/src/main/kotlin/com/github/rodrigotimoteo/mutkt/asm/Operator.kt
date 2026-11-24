package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodNode

/** One mutation opportunity: the [instruction] to change and a human-readable [description]. */
internal data class Site(
    val instruction: AbstractInsnNode,
    val description: String,
)

/** A bytecode mutation: [sites] discovers opportunities, [apply] performs one on the same [MethodNode]. */
internal interface Operator {
    val id: OperatorId

    /** Sites in instruction-list order; must not modify [method]. */
    fun sites(method: MethodNode): List<Site>

    /** Mutates [method] in place; [site] must come from [sites] on this same method. */
    fun apply(
        method: MethodNode,
        site: Site,
    )
}
