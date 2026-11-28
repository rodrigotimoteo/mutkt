package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.ClassInfo
import org.objectweb.asm.tree.MethodNode

internal const val CONTINUATION = "Lkotlin/coroutines/Continuation;"
internal const val INVOKE_SUSPEND = "invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;"

// Restricted suspend functions (sequence { } receivers) continue through RestrictedContinuationImpl instead.
private val CONTINUATION_IMPLS =
    setOf(
        "kotlin/coroutines/jvm/internal/ContinuationImpl",
        "kotlin/coroutines/jvm/internal/RestrictedContinuationImpl",
    )

/** A suspend function (its continuation is the last parameter and it returns Object) or a continuation's body. */
internal fun isSuspendShape(method: MethodNode): Boolean =
    method.desc.endsWith("$CONTINUATION)Ljava/lang/Object;") || method.name + method.desc == INVOKE_SUSPEND

/**
 * The `invokeSuspend` of the continuation class kotlinc generates for a suspend function: it stores the resume value,
 * then calls that function again and returns its result as is. A suspend lambda extends SuspendLambda instead, and its
 * invokeSuspend holds user code.
 */
internal fun isContinuationReentry(
    method: MethodNode,
    cls: ClassInfo,
): Boolean = method.name + method.desc == INVOKE_SUSPEND && cls.superName in CONTINUATION_IMPLS
