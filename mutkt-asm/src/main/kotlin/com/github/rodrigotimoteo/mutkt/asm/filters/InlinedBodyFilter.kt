package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.SiteFilter
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * The body of an inline function copied into a caller. An inline function's own method carries the same marker
 * for its whole body, so a marker named after the method is user code, as are lambda bodies inside any copy.
 */
internal object InlinedBodyFilter : SiteFilter {
    override fun noise(
        className: String,
        method: MethodNode,
    ): Set<AbstractInsnNode> {
        val own = FUNCTION_MARKER + method.name
        return innermostMarkers(method).filterValues { it.startsWith(FUNCTION_MARKER) && it != own }.keys.toSet()
    }
}
