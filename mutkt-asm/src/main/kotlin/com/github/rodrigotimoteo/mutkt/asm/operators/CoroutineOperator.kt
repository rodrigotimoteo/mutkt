package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Opcodes.PUTFIELD
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
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
 * re-entry of a continuation class (see isReentry).
 */
internal object CoroutineOperator : Operator {
    override val id = OperatorId("coroutine")

    override fun sites(method: MethodNode): List<Site> =
        candidateCalls(method)
            .filter { it.isCoroutineCall() && !isReentry(method) }
            .map { Site(it, it.describe("skip coroutine call:")) }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) = (site.instruction as MethodInsnNode).let { call ->
        val returned = Type.getReturnType(call.desc)
        method.replaceCall(call, returned.takeUnless { it == Type.VOID_TYPE }?.let { InsnNode(defaultOf(it)) })
    }

    // The continuation class the compiler generates for a suspend function stores the resume value in its `result`
    // field, then calls that function again and returns its result as is: dropping the call would end the
    // coroutine with null instead of resuming it. A suspend lambda also has an invokeSuspend with user calls, but
    // it keeps no `result` field. A method sees no class, so the superclass (ContinuationImpl) cannot be checked.
    private fun isReentry(method: MethodNode) =
        method.name == "invokeSuspend" &&
            method.desc == "(Ljava/lang/Object;)Ljava/lang/Object;" &&
            method.instructions.filterIsInstance<FieldInsnNode>().any {
                it.opcode == PUTFIELD && it.name == "result" && it.desc == "Ljava/lang/Object;"
            }
}
