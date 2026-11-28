package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.SiteFilter
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodNode

private const val RESUME_MESSAGE = "call to 'resume' before 'invoke' with coroutine"

/**
 * In a suspend function or `invokeSuspend`: the message of the IllegalStateException kotlinc throws when the state
 * machine's label matches no suspension point. The user can neither write nor reach it. No debug info needed.
 */
internal object ResumeMessageFilter : SiteFilter {
    override fun noise(
        className: String,
        method: MethodNode,
    ): Set<AbstractInsnNode> =
        if (isSuspendShape(method)) {
            method.instructions.filter { it is LdcInsnNode && it.cst == RESUME_MESSAGE }.toSet()
        } else {
            emptySet()
        }
}
