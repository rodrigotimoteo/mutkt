package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.SiteFilter
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * The copies of a finally body after the first (see [finallyTwins]): one finally site is one mutation
 * opportunity, applied to every copy by `mutate`, not one mutant per copy.
 */
internal object InlinedFinallyFilter : SiteFilter {
    override fun noise(
        className: String,
        method: MethodNode,
    ): Set<AbstractInsnNode> = finallyTwins(method).values.flatMapTo(HashSet()) { it }
}
