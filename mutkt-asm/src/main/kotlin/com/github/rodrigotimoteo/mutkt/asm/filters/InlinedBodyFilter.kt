package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.SiteFilter
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * The body of an inline function copied into a caller. An inline function's own method carries the same marker
 * for its whole body, which is user code, as are lambda bodies inside any copy. A regular method named like the
 * inline function it calls has no such marker of its own, so its copy is noise.
 */
internal object InlinedBodyFilter : SiteFilter {
    override fun noise(
        className: String,
        method: MethodNode,
    ): Set<AbstractInsnNode> {
        val own = (FUNCTION_MARKER + method.name).takeIf { isInlineFunctionOriginal(method) }
        return innermostMarkers(method).filterValues { it.startsWith(FUNCTION_MARKER) && it != own }.keys.toSet()
    }
}
