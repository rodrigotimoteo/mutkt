package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

// Restricted suspend functions (sequence { } receivers) continue through RestrictedContinuationImpl instead.
private val CONTINUATION_IMPLS =
    setOf(
        "kotlin/coroutines/jvm/internal/ContinuationImpl",
        "kotlin/coroutines/jvm/internal/RestrictedContinuationImpl",
    )

/**
 * Replaces a call to a kotlinx.coroutines builder or Job helper (`BuildersKt`, `JobKt`) or to a suspend function
 * (last parameter `Continuation`, returns `Object`) by its popped arguments and the return type's default.
 *
 * The call is replaced, never the suspension marker: `IntrinsicsKt.getCOROUTINE_SUSPENDED()` and the
 * comparisons against it belong to the compiler's state machine and stay untouched. The replaced call
 * "completes at once" with null, which a state machine already handles. The one call that must stay is the
 * re-entry of a continuation class (see isReentry).
 */
internal object CoroutineOperator : Operator {
    override val id = OperatorId("coroutine")

    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> =
        candidateCalls(method, cls)
            .filter { it.isCoroutineCall() && !isReentry(method, cls) }
            .map { Site(it, it.describe("skip coroutine call:")) }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) = (site.instruction as MethodInsnNode).let { call ->
        val returned = Type.getReturnType(call.desc)
        method.replaceCall(call, returned.takeUnless { it == Type.VOID_TYPE }?.let { InsnNode(defaultOf(it)) })
    }

    // The continuation class the compiler generates for a suspend function extends ContinuationImpl, stores the
    // resume value, then calls that function again and returns its result as is: dropping the call would end the
    // coroutine with null instead of resuming it. A suspend lambda extends SuspendLambda and its invokeSuspend
    // holds user calls.
    private fun isReentry(
        method: MethodNode,
        cls: ClassInfo,
    ) = method.name == "invokeSuspend" &&
        method.desc == "(Ljava/lang/Object;)Ljava/lang/Object;" &&
        cls.superName in CONTINUATION_IMPLS
}
