package com.github.rodrigotimoteo.mutkt.asm.operators

import org.objectweb.asm.Opcodes.ACONST_NULL
import org.objectweb.asm.Opcodes.DCONST_0
import org.objectweb.asm.Opcodes.FCONST_0
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.INVOKEINTERFACE
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Opcodes.LCONST_0
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

// Static entry points of kotlinx.coroutines: the builders (launch, async, runBlocking, withContext) and the Job
// helpers. GlobalScope has no statics; its builders are BuildersKt calls on GlobalScope.INSTANCE.
private val COROUTINE_OWNERS = setOf("kotlinx/coroutines/BuildersKt", "kotlinx/coroutines/JobKt")

// A suspend call ends in the Continuation and returns Object; with default arguments the compiler routes it
// through a static foo$default(..., Continuation, int, Object) bridge that has the same result.
private val SUSPEND_TAILS =
    listOf(
        "Lkotlin/coroutines/Continuation;)Ljava/lang/Object;",
        "Lkotlin/coroutines/Continuation;ILjava/lang/Object;)Ljava/lang/Object;",
    )

/** Calls that either call-removal operator may drop; the operator then filters on the return type. */
internal fun removableCalls(method: MethodNode): List<MethodInsnNode> =
    // Coroutine calls are the coroutine operator's sites, as copy calls are the data-class-copy operator's:
    // removing them here would give a second operator the same instruction and the same mutant.
    candidateCalls(method).filterNot { it.isCopyCall() || it.isCoroutineCall() }

/** Every call that may be dropped before the owning operator's own exclusions. */
internal fun candidateCalls(method: MethodNode): List<MethodInsnNode> {
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

/** A static call into a kotlinx.coroutines builder or Job helper, or a call of a suspend function. */
internal fun MethodInsnNode.isCoroutineCall(): Boolean =
    (opcode == INVOKESTATIC && owner in COROUTINE_OWNERS) || SUSPEND_TAILS.any(desc::endsWith)

/** The default value instruction of [type]: 0, 0L, 0f, 0.0 or null. */
internal fun defaultOf(type: Type) =
    when (type.sort) {
        Type.LONG -> LCONST_0
        Type.FLOAT -> FCONST_0
        Type.DOUBLE -> DCONST_0
        Type.OBJECT, Type.ARRAY -> ACONST_NULL
        else -> ICONST_0
    }

/**
 * A call of the shape Kotlin emits for a data class copy: `owner.copy(...)` returning `owner`, or the static
 * `owner.copy$default(owner, ..., int, Object)` returning `owner`. Decided from the call alone, so a plain
 * method named `copy` that returns its own class has the same shape.
 */
internal fun MethodInsnNode.isCopyCall(): Boolean {
    val own = Type.getObjectType(owner)
    return Type.getReturnType(desc) == own &&
        when (opcode) {
            INVOKEVIRTUAL -> name == "copy" && Type.getArgumentTypes(desc).isNotEmpty()
            INVOKESTATIC -> name == "copy\$default" && isDefaultBridgeOf(own)
            else -> false
        }
}

// The bridge takes the receiver, the copy arguments, then the default mask and a marker Object.
private fun MethodInsnNode.isDefaultBridgeOf(own: Type): Boolean {
    val args = Type.getArgumentTypes(desc).toList()
    val tail = listOf(Type.INT_TYPE, Type.getObjectType("java/lang/Object"))
    return args.firstOrNull() == own && args.takeLast(2) == tail && args.drop(1).dropLast(2).isNotEmpty()
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
