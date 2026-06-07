package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.classloader.MutantClassLoaderFactory
import com.github.rodrigotimoteo.mutation.mutator.MutationInfo
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import com.github.rodrigotimoteo.mutation.mutator.Mutator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

/**
 * Verifies that MutantClassLoader makes mutations VISIBLE to code loaded by parent.
 */
class MutantVisibilityTest {
    @Test
    fun `mutation is visible to code loaded by parent classloader`() {
        // Build TargetClass: getValue() returns 42
        val targetCW = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        targetCW.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "target/TargetClass", null, "java/lang/Object", null)
        val initMV = targetCW.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        initMV.visitCode()
        initMV.visitVarInsn(Opcodes.ALOAD, 0)
        initMV.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        initMV.visitInsn(Opcodes.RETURN)
        initMV.visitMaxs(1, 1)
        initMV.visitEnd()

        val getValMV =
            targetCW.visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "getValue",
                "()I",
                null,
                null,
            )
        getValMV.visitCode()
        getValMV.visitIntInsn(Opcodes.BIPUSH, 42)
        getValMV.visitInsn(Opcodes.IRETURN)
        getValMV.visitMaxs(1, 0)
        getValMV.visitEnd()
        targetCW.visitEnd()

        val originalTargetBytes = targetCW.toByteArray()

        // Build CallerClass: callGetValue() calls TargetClass.getValue()
        val callerCW = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        callerCW.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "caller/CallerClass", null, "java/lang/Object", null)
        val callerInit = callerCW.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        callerInit.visitCode()
        callerInit.visitVarInsn(Opcodes.ALOAD, 0)
        callerInit.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        callerInit.visitInsn(Opcodes.RETURN)
        callerInit.visitMaxs(1, 1)
        callerInit.visitEnd()

        val callMV =
            callerCW.visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "callGetValue",
                "()I",
                null,
                null,
            )
        callMV.visitCode()
        callMV.visitMethodInsn(Opcodes.INVOKESTATIC, "target/TargetClass", "getValue", "()I", false)
        callMV.visitInsn(Opcodes.IRETURN)
        callMV.visitMaxs(1, 0)
        callMV.visitEnd()
        callerCW.visitEnd()

        val callerBytes = callerCW.toByteArray()

        // Manually mutate: BIPUSH 42 -> BIPUSH 99
        val mutatedTargetBytes = originalTargetBytes.copyOf()
        var found = false
        for (i in mutatedTargetBytes.indices) {
            if (mutatedTargetBytes[i] == Opcodes.BIPUSH.toByte()) {
                if (i + 1 < mutatedTargetBytes.size && mutatedTargetBytes[i + 1] == 42.toByte()) {
                    mutatedTargetBytes[i + 1] = 99.toByte()
                    found = true
                    break
                }
            }
        }
        assertTrue(found, "Should have found BIPUSH 42 to mutate")

        val classFiles =
            mapOf(
                "target/TargetClass" to mutatedTargetBytes,
                "caller/CallerClass" to callerBytes,
            )

        val mutationInfo =
            MutationInfo(
                operator = MutationOperator.ARITHMETIC,
                className = "target.TargetClass",
                methodName = "getValue",
                methodDescriptor = "()I",
                lineNumber = 0,
                description = "42 to 99",
                originalOpcode = Opcodes.BIPUSH,
                mutatedOpcode = Opcodes.BIPUSH,
            )

        val parentLoader = javaClass.classLoader
        val mutator = Mutator(setOf(MutationOperator.ARITHMETIC))
        val classLoader = MutantClassLoaderFactory.create(parentLoader, classFiles, mutationInfo, mutator)

        val callerClass = classLoader.loadClass("caller.CallerClass")
        val result = callerClass.getMethod("callGetValue").invoke(null) as Int
        assertEquals(99, result, "CallerClass should see mutated TargetClass via MutantClassLoader")
    }
}
