package com.github.rodrigotimoteo.mutkt.asm

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodNode

/** A rule that recognises instructions the compiler generated; sites on them are not mutation opportunities. */
internal interface SiteFilter {
    /** The instructions of [method] (in class [className]) that are compiler-generated; must not modify it. */
    fun noise(
        className: String,
        method: MethodNode,
    ): Set<AbstractInsnNode>
}

/**
 * The sites of [op] in [method] that are not on noise. Ordinals index this list, so [discover] and [mutate]
 * agree on them whatever the filters drop.
 */
internal fun sitesOf(
    op: Operator,
    className: String,
    method: MethodNode,
): List<Site> {
    val sites = op.sites(method)
    if (sites.isEmpty()) return sites
    val noise = SiteFilters.all.flatMapTo(HashSet()) { it.noise(className, method) }
    return sites.filterNot { it.instruction in noise }
}
