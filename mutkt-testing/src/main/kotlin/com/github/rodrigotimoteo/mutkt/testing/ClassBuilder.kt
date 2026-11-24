package com.github.rodrigotimoteo.mutkt.testing

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.V17

/** Builds a public class (internal [name]) with one public static method whose body is written by [code]. */
fun buildClass(
    name: String,
    method: String,
    descriptor: String = "()I",
    code: MethodVisitor.() -> Unit,
): ByteArray {
    // Frames and maxs are computed so a test only writes the instructions it cares about.
    val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
    writer.visit(V17, ACC_PUBLIC, name, null, "java/lang/Object", null)
    writer.visitMethod(ACC_PUBLIC or ACC_STATIC, method, descriptor, null, null).apply {
        visitCode()
        code()
        visitMaxs(0, 0)
        visitEnd()
    }
    writer.visitEnd()
    return writer.toByteArray()
}
