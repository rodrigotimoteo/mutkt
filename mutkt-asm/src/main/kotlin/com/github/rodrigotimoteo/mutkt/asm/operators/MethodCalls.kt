package com.github.rodrigotimoteo.mutkt.asm.operators

import org.objectweb.asm.Opcodes.INVOKEINTERFACE
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Opcodes.NOP
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.Opcodes.POP2
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

// INVOKESPECIAL is a site only for a private method of the class itself (owner is the class): constructors
// and super calls cannot be dropped without breaking initialization or the inheritance contract.
// Compiler plumbing (null-check Intrinsics, inline markers, coroutine state machine helpers) is not user
// logic; null checks belong to the null-safety operator.
private val CALL_OPCODES = setOf(INVOKEVIRTUAL, INVOKESTATIC, INVOKEINTERFACE)
private val PLUMBING_OWNERS =
    listOf(
        "kotlin/jvm/internal/",
        "kotlin/coroutines/intrinsics/",
        "kotlin/coroutines/jvm/internal/",
        "kotlin/ResultKt",
    )

/** Calls that either call-removal operator may drop; the operator then filters on the return type. */
internal fun removableCalls(method: MethodNode): List<MethodInsnNode> {
    // The operator contract gives no class, so the owner comes from the type of local 0 (`this`) in the debug
    // info. Without it no INVOKESPECIAL is a site, which is safe.
    val self =
        method.localVariables
            ?.firstOrNull {
                it.index == 0 && it.name == "this"
            }?.let { Type.getType(it.desc).internalName }
    return method.instructions
        .filterIsInstance<MethodInsnNode>()
        .filter { it.opcode in CALL_OPCODES || (it.opcode == INVOKESPECIAL && it.name != "<init>" && it.owner == self) }
        .filter { PLUMBING_OWNERS.none(it.owner::startsWith) }
}

internal fun MethodInsnNode.describe(action: String) = "$action ${owner.substringAfterLast('/')}.$name"

/** Replaces [call] by pops for its arguments and receiver, then [result] if any; a NOP keeps try ranges non-empty. */
internal fun MethodNode.replaceCall(
    call: MethodInsnNode,
    result: AbstractInsnNode?,
) {
    val operands =
        Type.getArgumentTypes(call.desc).map { it.size }.reversed() +
            listOfNotNull(1.takeIf { call.opcode != INVOKESTATIC })
    val replacement =
        InsnList().apply {
            operands.forEach { add(InsnNode(if (it == 2) POP2 else POP)) }
            result?.let(::add)
            if (size() == 0) add(InsnNode(NOP))
        }
    instructions.insertBefore(call, replacement)
    instructions.remove(call)
}
