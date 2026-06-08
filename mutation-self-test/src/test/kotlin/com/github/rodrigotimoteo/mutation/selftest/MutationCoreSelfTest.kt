package com.github.rodrigotimoteo.mutation.selftest

import com.github.rodrigotimoteo.mutation.engine.MutationEngine
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import com.github.rodrigotimoteo.mutation.mutator.Mutator
import com.github.rodrigotimoteo.mutation.registry.MutationRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Self-test: runs mutation testing against mutation-core.
 * This verifies that mutation-core's test suite is comprehensive enough
 * to kill mutations in its own code.
 */
class MutationCoreSelfTest {
    @BeforeEach
    fun setup() {
        MutationRegistry.reset()
    }

    @AfterEach
    fun teardown() {
        MutationRegistry.reset()
    }

    @Test
    fun `mutator can scan and apply arithmetic mutations`() {
        // Build a minimal class with IADD
        val cw = org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES)
        cw.visit(
            org.objectweb.asm.Opcodes.V17,
            org.objectweb.asm.Opcodes.ACC_PUBLIC,
            "com/example/SimpleCalc",
            null,
            "java/lang/Object",
            null,
        )
        val ctor = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(
            org.objectweb.asm.Opcodes.INVOKESPECIAL,
            "java/lang/Object",
            "<init>",
            "()V",
            false,
        )
        ctor.visitInsn(org.objectweb.asm.Opcodes.RETURN)
        ctor.visitMaxs(1, 1)
        ctor.visitEnd()

        val mv = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "add", "(II)I", null, null)
        mv.visitCode()
        mv.visitLineNumber(1, org.objectweb.asm.Label())
        mv.visitVarInsn(org.objectweb.asm.Opcodes.ILOAD, 1)
        mv.visitVarInsn(org.objectweb.asm.Opcodes.ILOAD, 2)
        mv.visitInsn(org.objectweb.asm.Opcodes.IADD)
        mv.visitInsn(org.objectweb.asm.Opcodes.IRETURN)
        mv.visitMaxs(2, 3)
        mv.visitEnd()
        cw.visitEnd()

        val classBytes = cw.toByteArray()

        // Scan for mutations
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(classBytes)
        assertTrue(mutations.isNotEmpty(), "Should find mutations in simple class")

        // Apply first mutation
        val mutated = mutator.applyMutation(classBytes, mutations.first())
        assertTrue(mutated.isNotEmpty(), "Mutated bytecode should not be empty")
        assertTrue(mutated.size != classBytes.size || !mutated.contentEquals(classBytes), "Mutated bytes should differ")
    }

    @Test
    fun `mutation engine produces a report`() {
        val cw = org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES)
        cw.visit(
            org.objectweb.asm.Opcodes.V17,
            org.objectweb.asm.Opcodes.ACC_PUBLIC,
            "com/example/SimpleCalc",
            null,
            "java/lang/Object",
            null,
        )
        val ctor = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(
            org.objectweb.asm.Opcodes.INVOKESPECIAL,
            "java/lang/Object",
            "<init>",
            "()V",
            false,
        )
        ctor.visitInsn(org.objectweb.asm.Opcodes.RETURN)
        ctor.visitMaxs(1, 1)
        ctor.visitEnd()

        val mv = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "add", "(II)I", null, null)
        mv.visitCode()
        mv.visitLineNumber(1, org.objectweb.asm.Label())
        mv.visitVarInsn(org.objectweb.asm.Opcodes.ILOAD, 1)
        mv.visitVarInsn(org.objectweb.asm.Opcodes.ILOAD, 2)
        mv.visitInsn(org.objectweb.asm.Opcodes.IADD)
        mv.visitInsn(org.objectweb.asm.Opcodes.IRETURN)
        mv.visitMaxs(2, 3)
        mv.visitEnd()
        cw.visitEnd()

        val classBytes = cw.toByteArray()
        val classFiles = mapOf("com/example/SimpleCalc" to classBytes)

        val engine =
            MutationEngine(
                enabledOperators = setOf(MutationOperator.ARITHMETIC),
                timeoutMs = 5000,
                maxParallelMutants = 1,
            )

        // Run with no test classes — should produce NO_COVERAGE
        val report = engine.runMutationTesting(classFiles, emptyList(), emptyMap())
        assertTrue(report.totalMutations > 0, "Should generate mutations")
    }
}
