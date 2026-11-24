package com.github.rodrigotimoteo.mutkt.asm

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

internal fun parse(bytes: ByteArray): ClassNode = ClassNode().also { ClassReader(bytes).accept(it, 0) }
