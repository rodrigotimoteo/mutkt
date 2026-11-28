package com.github.rodrigotimoteo.mutkt.asm

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.security.MessageDigest

/**
 * SHA-256 of each method's code in [classBytes], keyed by name and descriptor (`run()I`). Line numbers, local
 * variable names and frames are left out, so an edit that only moves lines keeps every digest.
 */
public fun methodDigests(classBytes: ByteArray): Map<String, String> {
    val node = ClassNode()
    // Without debug info the reader creates labels only for jump and handler targets, so moved lines leave no trace.
    ClassReader(classBytes).accept(node, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    return node.methods.associate { "${it.name}${it.desc}" to digest(it) }
}

// The method alone in an empty class: its constant pool then holds only what this method's code uses.
private fun digest(method: MethodNode): String {
    val writer = ClassWriter(0)
    writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "M", null, "java/lang/Object", null)
    method.accept(writer)
    writer.visitEnd()
    return MessageDigest.getInstance("SHA-256").digest(writer.toByteArray()).joinToString("") { "%02x".format(it) }
}
