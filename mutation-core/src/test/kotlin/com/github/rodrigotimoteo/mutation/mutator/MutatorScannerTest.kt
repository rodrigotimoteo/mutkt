package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class MutatorScannerTest {
    @Test
    fun `raw ASM should visit instructions`() {
        val classBytes = createAddClass()

        val reader = org.objectweb.asm.ClassReader(classBytes)
        var count = 0

        reader.accept(
            object : org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): org.objectweb.asm.MethodVisitor? {
                    if (name == "<init>") return null
                    return object : org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                        override fun visitInsn(opcode: Int) {
                            count++
                            println("  RAW visitInsn: opcode=$opcode")
                            super.visitInsn(opcode)
                        }
                    }
                }
            },
            0,
        )

        println("RAW count: $count")
        assertTrue(count > 0, "Raw ASM should visit instructions, got $count")
    }

    @Test
    fun `scan should find mutations in simple method`() {
        // Manually create a simple class with an add method
        val classBytes = createAddClass()

        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(classBytes)

        println("Found ${mutations.size} mutations")
        mutations.forEach {
            println("  ${it.operator.operatorName} ${it.methodName}:${it.originalOpcode} -> ${it.mutatedOpcode} - ${it.description}")
        }

        assertTrue(mutations.isNotEmpty(), "Should find at least one mutation")
    }

    private fun createAddClass(): ByteArray {
        val cw = org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES)
        cw.visit(org.objectweb.asm.Opcodes.V17, org.objectweb.asm.Opcodes.ACC_PUBLIC, "TestClass", null, "java/lang/Object", null)

        // Constructor
        val initMv = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        initMv.visitCode()
        initMv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0)
        initMv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        initMv.visitInsn(org.objectweb.asm.Opcodes.RETURN)
        initMv.visitMaxs(1, 1)
        initMv.visitEnd()

        // add(II)I method
        val addMv = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "add", "(II)I", null, null)
        addMv.visitCode()
        addMv.visitVarInsn(org.objectweb.asm.Opcodes.ILOAD, 1)
        addMv.visitVarInsn(org.objectweb.asm.Opcodes.ILOAD, 2)
        addMv.visitInsn(org.objectweb.asm.Opcodes.IADD)
        addMv.visitInsn(org.objectweb.asm.Opcodes.IRETURN)
        addMv.visitMaxs(2, 3)
        addMv.visitEnd()

        cw.visitEnd()
        return cw.toByteArray()
    }
}
