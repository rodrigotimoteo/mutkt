package com.github.rodrigotimoteo.mutkt.asm

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode

internal fun ClassNode.toBytes(): ByteArray = ClassWriter(ClassWriter.COMPUTE_MAXS).also { accept(it) }.toByteArray()
