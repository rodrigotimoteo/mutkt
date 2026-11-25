package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Operator
import com.github.rodrigotimoteo.mutkt.asm.Site
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

/** Removes calls to void methods, discarding their arguments and receiver. */
internal object VoidMethodCallsOperator : Operator {
    override val id = OperatorId("void-method-calls")

    override fun sites(method: MethodNode): List<Site> =
        removableCalls(method)
            .filter { Type.getReturnType(it.desc) == Type.VOID_TYPE }
            .map { Site(it, it.describe("remove call")) }

    override fun apply(
        method: MethodNode,
        site: Site,
    ) = method.replaceCall(site.instruction as MethodInsnNode, null)
}
