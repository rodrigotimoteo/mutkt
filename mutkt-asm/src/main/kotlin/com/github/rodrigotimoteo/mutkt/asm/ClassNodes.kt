package com.github.rodrigotimoteo.mutkt.asm

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

internal fun parse(bytes: ByteArray): ClassNode = ClassNode().also { ClassReader(bytes).accept(it, 0) }

/** The class's `SourceFile` attribute: the file name it was compiled from, or null when it has none. */
public fun sourceFileOf(bytes: ByteArray): String? =
    ClassNode().also { ClassReader(bytes).accept(it, ClassReader.SKIP_CODE) }.sourceFile
