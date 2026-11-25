package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.SiteFilter
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * A class kotlinc regenerates in the caller for an inline function's object (`Caller$f$$inlined$g$1`). Only the
 * lambda bodies the user passed are user code there. Without debug info the lambdas cannot be told from the
 * copy, so nothing is noise.
 */
internal object InlinedClassFilter : SiteFilter {
    override fun noise(
        className: String,
        method: MethodNode,
    ): Set<AbstractInsnNode> {
        if ("\$\$inlined\$" !in className || method.localVariables.isNullOrEmpty()) return emptySet()
        val markers = innermostMarkers(method)
        return method.instructions.filterNot { markers[it]?.startsWith(LAMBDA_MARKER) == true }.toSet()
    }
}
