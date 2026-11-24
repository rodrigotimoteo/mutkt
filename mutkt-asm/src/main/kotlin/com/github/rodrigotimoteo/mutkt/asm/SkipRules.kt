package com.github.rodrigotimoteo.mutkt.asm

import org.objectweb.asm.Opcodes.ACC_ABSTRACT
import org.objectweb.asm.Opcodes.ACC_BRIDGE
import org.objectweb.asm.Opcodes.ACC_NATIVE
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC
import org.objectweb.asm.tree.MethodNode

private const val NO_BODY_OR_GENERATED = ACC_SYNTHETIC or ACC_BRIDGE or ACC_ABSTRACT or ACC_NATIVE

/** False for compiler-generated methods, methods without code, and initializers. */
internal fun isMutable(method: MethodNode): Boolean =
    (method.access and NO_BODY_OR_GENERATED) == 0 && method.name != "<init>" && method.name != "<clinit>"
