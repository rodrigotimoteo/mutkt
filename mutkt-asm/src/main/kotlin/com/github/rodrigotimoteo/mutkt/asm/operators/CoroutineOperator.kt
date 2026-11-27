package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.asm.filters.isContinuationReentry
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * Replaces a call to a kotlinx.coroutines builder or Job helper (`BuildersKt`, `JobKt`) or to a suspend function
 * (last parameter `Continuation`, returns `Object`) by its popped arguments and the return type's default.
 *
 * The call is replaced, never the suspension marker: `IntrinsicsKt.getCOROUTINE_SUSPENDED()` and the
 * comparisons against it belong to the compiler's state machine and stay untouched. The replaced call
 * "completes at once" with null, which a state machine already handles. The one call that must stay is the
 * re-entry of a continuation class (isContinuationReentry): dropping it would end the coroutine with null
 * instead of resuming it.
 */
internal object CoroutineOperator : Operator {
    override val id = OperatorId("coroutine")

    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> =
        candidateCalls(method, cls)
            .filter { it.isCoroutineCall() && !isContinuationReentry(method, cls) }
            .map { Site(it, it.describe("skip coroutine call:")) }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) = (site.instruction as MethodInsnNode).let { call ->
        val returned = Type.getReturnType(call.desc)
        method.replaceCall(call, returned.takeUnless { it == Type.VOID_TYPE }?.let { InsnNode(defaultOf(it)) })
    }
}
