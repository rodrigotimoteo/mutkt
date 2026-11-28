package com.github.rodrigotimoteo.mutkt.asm.filters

import org.objectweb.asm.tree.MethodNode

internal const val CONTINUATION = "Lkotlin/coroutines/Continuation;"
internal const val INVOKE_SUSPEND = "invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;"

/** A suspend function (its continuation is the last parameter and it returns Object) or a continuation's body. */
internal fun isSuspendShape(method: MethodNode): Boolean =
    method.desc.endsWith("$CONTINUATION)Ljava/lang/Object;") || method.name + method.desc == INVOKE_SUSPEND
