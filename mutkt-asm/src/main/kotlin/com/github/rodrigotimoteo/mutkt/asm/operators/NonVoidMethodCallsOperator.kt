package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

private const val CONTINUATION = "Lkotlin/coroutines/Continuation;"

/** Replaces a non-void call by the default of its return type (0, 0L, 0f, 0.0, false, null). */
internal object NonVoidMethodCallsOperator : Operator {
    override val id = OperatorId("non-void-method-calls")

    override fun sites(
        method: MethodNode,
        cls: ClassInfo,
    ): List<Site> =
        removableCalls(method, cls)
            .filter(::isCandidate)
            .map { Site(it, it.describe("replace call with default:")) }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) = (site.instruction as MethodInsnNode).let {
        method.replaceCall(it, InsnNode(defaultOf(Type.getReturnType(it.desc))))
    }

    // kotlin/Unit has one value, so a null in its place is noise. A suspend call may return the suspension
    // marker, which a default would silently swallow.
    private fun isCandidate(call: MethodInsnNode): Boolean {
        val returned = Type.getReturnType(call.desc)
        val suspends = Type.getArgumentTypes(call.desc).lastOrNull()?.descriptor == CONTINUATION
        return returned != Type.VOID_TYPE && returned.descriptor != "Lkotlin/Unit;" && !suspends
    }
}
