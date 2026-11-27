package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.SiteFilter
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ARETURN
import org.objectweb.asm.Opcodes.ASTORE
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.IF_ACMPNE
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode

// The generic signature keeps the declared return type of a suspend function: Continuation<? super Unit>.
private const val UNIT_CONTINUATION = "Lkotlin/coroutines/Continuation<-Lkotlin/Unit;>;)"

/**
 * In a suspend function or `invokeSuspend`: each `IF_ACMPNE` whose right operand is `COROUTINE_SUSPENDED` (the
 * local kotlinc stores it in, or a direct `getCOROUTINE_SUSPENDED()` call after a tail call) with the `ARETURN` it
 * falls through to, any `ARETURN` of that local, and `return Unit.INSTANCE` in a suspend function declared to
 * return Unit. The user can reach none of them from source. A lambda's Unit return stays: its declared type is in
 * the class signature, which a filter does not see. No debug info needed.
 */
internal object SuspendMarkerFilter : SiteFilter {
    override fun noise(
        className: String,
        method: MethodNode,
    ): Set<AbstractInsnNode> {
        if (!isSuspendShape(method)) return emptySet()
        val marker = markerLocals(method)
        val unit = method.signature?.contains(UNIT_CONTINUATION) == true
        return method.instructions
            .flatMap { insn ->
                val prev = realBefore(insn)
                when {
                    insn.opcode == IF_ACMPNE && prev.isMarker(marker) ->
                        listOfNotNull(insn, realAfter(insn)?.takeIf { it.opcode == ARETURN })
                    insn.opcode == ARETURN && (prev.isMarker(marker) || (unit && isUnitValue(prev))) -> listOf(insn)
                    else -> emptyList()
                }
            }.toSet()
    }

    private fun markerLocals(method: MethodNode): Set<Int> =
        method.instructions
            .filter { isMarkerCall(it) }
            .mapNotNull { (realAfter(it) as? VarInsnNode)?.takeIf { store -> store.opcode == ASTORE }?.`var` }
            .toSet()

    private fun isMarkerCall(insn: AbstractInsnNode?) =
        insn is MethodInsnNode &&
            insn.opcode == INVOKESTATIC &&
            insn.owner == "kotlin/coroutines/intrinsics/IntrinsicsKt" &&
            insn.name == "getCOROUTINE_SUSPENDED"

    private fun AbstractInsnNode?.isMarker(marker: Set<Int>) =
        isMarkerCall(this) || (this is VarInsnNode && opcode == ALOAD && `var` in marker)

    private fun isUnitValue(insn: AbstractInsnNode?) =
        insn is FieldInsnNode && insn.opcode == GETSTATIC && insn.owner == "kotlin/Unit" && insn.name == "INSTANCE"
}
