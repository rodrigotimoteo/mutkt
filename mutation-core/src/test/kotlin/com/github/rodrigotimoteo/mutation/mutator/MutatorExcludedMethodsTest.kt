package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

class MutatorExcludedMethodsTest {
    private lateinit var mutator: Mutator

    @BeforeEach
    fun setup() {
        mutator =
            Mutator(
                enabledOperators = MutationOperator.MVP_OPERATORS,
                excludedMethods = setOf("toString", "hashCode", "equals"),
            )
    }

    @Test
    fun `excluded methods are not scanned for mutations`() {
        val classBytes =
            buildClassWithMethod(
                "toString",
                "()Ljava/lang/String;",
                listOf(Opcodes.ICONST_1, Opcodes.IRETURN),
            )

        val mutations = mutator.scanMutations(classBytes)
        assertTrue(
            mutations.none { it.methodName == "toString" },
            "toString should be excluded from mutations",
        )
    }

    @Test
    fun `non-excluded methods are still scanned`() {
        val classBytes =
            buildClassWithMethod(
                "calculate",
                "()I",
                listOf(Opcodes.ICONST_1, Opcodes.IRETURN),
            )

        val mutations = mutator.scanMutations(classBytes)
        assertTrue(
            mutations.any { it.methodName == "calculate" },
            "calculate should still produce mutations",
        )
    }

    @Test
    fun `excludedMethods uses contains matching`() {
        // "toStr" should match toString via contains
        val mutatorWithPartial =
            Mutator(
                enabledOperators = MutationOperator.MVP_OPERATORS,
                excludedMethods = setOf("toStr"),
            )

        val classBytes =
            buildClassWithMethod(
                "toString",
                "()Ljava/lang/String;",
                listOf(Opcodes.ICONST_1, Opcodes.IRETURN),
            )

        val mutations = mutatorWithPartial.scanMutations(classBytes)
        assertTrue(
            mutations.none { it.methodName == "toString" },
            "toString should be excluded by partial match 'toStr'",
        )
    }

    @Test
    fun `empty excludedMethods includes all methods`() {
        val mutatorWithEmpty =
            Mutator(
                enabledOperators = MutationOperator.MVP_OPERATORS,
                excludedMethods = emptySet(),
            )

        val classBytes =
            buildClassWithMethod(
                "toString",
                "()Ljava/lang/String;",
                listOf(Opcodes.ICONST_1, Opcodes.IRETURN),
            )

        val mutations = mutatorWithEmpty.scanMutations(classBytes)
        assertTrue(
            mutations.any { it.methodName == "toString" },
            "toString should be included when excludedMethods is empty",
        )
    }

    private fun buildClassWithMethod(
        methodName: String,
        descriptor: String,
        instructions: List<Int>,
    ): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER,
            "com/example/TestClass",
            null,
            "java/lang/Object",
            null,
        )
        cw.visitField(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "value",
            "I",
            null,
            null,
        ).visitEnd()

        val initVisitor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        initVisitor.visitCode()
        initVisitor.visitVarInsn(Opcodes.ALOAD, 0)
        initVisitor.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/Object",
            "<init>",
            "()V",
            false,
        )
        initVisitor.visitInsn(Opcodes.RETURN)
        initVisitor.visitMaxs(1, 1)
        initVisitor.visitEnd()

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, methodName, descriptor, null, null)
        mv.visitCode()
        mv.visitLineNumber(10, org.objectweb.asm.Label())
        for (insn in instructions) {
            when (insn) {
                Opcodes.ICONST_0 -> mv.visitInsn(Opcodes.ICONST_0)
                Opcodes.ICONST_1 -> mv.visitInsn(Opcodes.ICONST_1)
                Opcodes.IRETURN -> mv.visitInsn(Opcodes.IRETURN)
                else -> mv.visitVarInsn(Opcodes.ALOAD, 0)
            }
        }
        mv.visitMaxs(2, 1)
        mv.visitEnd()

        cw.visitEnd()
        return cw.toByteArray()
    }
}
