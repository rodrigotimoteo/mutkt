package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutatorSuppressionTest {
    @Test
    fun `classSuppressed suppresses all mutations`() {
        val bytes = buildClassWithClassLevelSuppression()
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        assertEquals(0, mutations.size, "Class-level @SuppressMutations should suppress all")
    }

    @Test
    fun `operator suppression on class suppresses only listed operators`() {
        // Build a class with both ARITHMETIC (IADD) and CONDITIONALS_BOUNDARY (IFGE)
        val bytes = buildClassWithBothArithmeticAndConditional()
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val allMutations = mutator.scanMutations(bytes)
        assertTrue(allMutations.isNotEmpty(), "Should have mutations before suppression")
        assertTrue(
            allMutations.any { it.operator == MutationOperator.ARITHMETIC },
            "Should have ARITHMETIC before suppression",
        )
        assertTrue(
            allMutations.any { it.operator == MutationOperator.CONDITIONALS_BOUNDARY },
            "Should have CONDITIONALS_BOUNDARY before suppression",
        )

        // Now suppress only ARITHMETIC
        val bytesSuppressed = buildClassWithOperatorSuppression(listOf("ARITHMETIC"))
        val mutations = mutator.scanMutations(bytesSuppressed)
        // ARITHMETIC suppressed, but other operators still present
        assertTrue(
            mutations.none { it.operator == MutationOperator.ARITHMETIC },
            "ARITHMETIC should be suppressed",
        )
        assertTrue(
            mutations.any { it.operator == MutationOperator.CONDITIONALS_BOUNDARY },
            "CONDITIONALS_BOUNDARY should NOT be suppressed",
        )
    }

    @Test
    fun `class without SuppressMutations annotation allows all operators`() {
        val bytes = buildClassWithIAdd()
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        // Some mutations should exist
        assertTrue(mutations.isNotEmpty())
    }

    @Test
    fun `class with empty operator list in SuppressMutations still suppresses all`() {
        // @SuppressMutations() with no operators is treated as suppress-all
        val bytes = buildClassWithOperatorSuppression(emptyList())
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        assertEquals(0, mutations.size)
    }

    @Test
    fun `multiple suppressed operators suppress only listed ones`() {
        val bytes = buildClassWithBothArithmeticAndConditional()
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val allMutations = mutator.scanMutations(bytes)
        assertTrue(allMutations.isNotEmpty(), "Should have mutations before suppression")

        // Suppress only ARITHMETIC and RETURN_VALS — CONDITIONALS_BOUNDARY should remain
        val suppressedBytes = buildClassWithOperatorSuppression(listOf("ARITHMETIC", "RETURN_VALS"))
        val mutations = mutator.scanMutations(suppressedBytes)
        // Only ARITHMETIC and RETURN_VALS suppressed, other operators remain
        assertTrue(
            mutations.none { it.operator == MutationOperator.ARITHMETIC },
            "ARITHMETIC should be suppressed",
        )
        assertTrue(
            mutations.any { it.operator == MutationOperator.CONDITIONALS_BOUNDARY },
            "CONDITIONALS_BOUNDARY should NOT be suppressed",
        )
    }

    @Test
    fun `unknown operator in SuppressMutations has no effect`() {
        // @SuppressMutations(operators=["DOES_NOT_EXIST"]) — suppresses nothing
        val bytes = buildClassWithOperatorSuppression(listOf("DOES_NOT_EXIST"))
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)
        // Unknown operator suppressed, but class has real mutations that remain
        assertTrue(mutations.isNotEmpty(), "Real mutations should NOT be suppressed by unknown operator")
    }

    @Test
    fun `method-level SuppressMutations suppresses all mutations for that method only`() {
        // Class with two methods: one suppressed (no operators), one not
        val bytes = buildClassWithMethodLevelSuppression()
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val mutations = mutator.scanMutations(bytes)

        // The suppressed method "add" has IADD → should produce 0 ARITHMETIC mutations
        assertTrue(
            mutations.none { it.methodName == "add" },
            "Suppressed method 'add' should have no mutations",
        )

        // The unsuppressed method "sub" has ISUB → should produce ARITHMETIC mutations
        assertTrue(
            mutations.any { it.methodName == "sub" },
            "Unsuppressed method 'sub' should still have mutations",
        )
    }

    @Test
    fun `method-level SuppressMutations with operators suppresses only listed ones`() {
        // Two methods: "mixed" has both IADD and IFGE (suppress only ARITHMETIC)
        val bytes = buildClassWithMethodOperatorSuppression()
        val mutator =
            Mutator(
                enabledOperators = setOf(MutationOperator.ARITHMETIC, MutationOperator.CONDITIONALS_BOUNDARY),
            )
        val mutations = mutator.scanMutations(bytes)

        // ARITHMETIC suppressed on the "mixed" method
        assertTrue(
            mutations.none { it.methodName == "mixed" && it.operator == MutationOperator.ARITHMETIC },
            "ARITHMETIC should be suppressed on 'mixed' method",
        )
        // CONDITIONALS_BOUNDARY still present
        assertTrue(
            mutations.any { it.methodName == "mixed" && it.operator == MutationOperator.CONDITIONALS_BOUNDARY },
            "CONDITIONALS_BOUNDARY should NOT be suppressed on 'mixed' method",
        )
        // The unsuppressed method "add" still has ARITHMETIC
        assertTrue(
            mutations.any { it.methodName == "add" && it.operator == MutationOperator.ARITHMETIC },
            "Unsuppressed method 'add' should still have ARITHMETIC",
        )
    }

    @Test
    fun `combined class-level and method-level suppression both work`() {
        // Class suppresses RETURN_VALS, method "add" suppresses ARITHMETIC
        val bytes = buildClassWithCombinedSuppression()
        val mutator =
            Mutator(
                enabledOperators = setOf(MutationOperator.ARITHMETIC, MutationOperator.CONDITIONALS_BOUNDARY, MutationOperator.RETURN_VALS),
            )
        val mutations = mutator.scanMutations(bytes)

        // Class-level: RETURN_VALS suppressed everywhere
        assertTrue(
            mutations.none { it.operator == MutationOperator.RETURN_VALS },
            "RETURN_VALS should be suppressed by class-level annotation",
        )
        // Method "add": ARITHMETIC suppressed (method-level)
        assertTrue(
            mutations.none { it.methodName == "add" && it.operator == MutationOperator.ARITHMETIC },
            "ARITHMETIC should be suppressed on 'add' by method-level annotation",
        )
        // Method "sub": ARITHMETIC NOT suppressed
        assertTrue(
            mutations.any { it.methodName == "sub" && it.operator == MutationOperator.ARITHMETIC },
            "ARITHMETIC should NOT be suppressed on 'sub'",
        )
    }

    private fun buildClassWithIAdd(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, org.objectweb.asm.Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithConditionalOperator(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "(I)Z", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, org.objectweb.asm.Label())
        mv?.visitVarInsn(Opcodes.ILOAD, 1)
        val label = org.objectweb.asm.Label()
        mv?.visitJumpInsn(Opcodes.IFGE, label)
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitLabel(label)
        mv?.visitInsn(Opcodes.ICONST_0)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(1, 2)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithBothArithmeticAndConditional(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        // Method with both IADD (ARITHMETIC) and IFGE (CONDITIONALS_BOUNDARY)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "(I)I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, org.objectweb.asm.Label())
        mv?.visitVarInsn(Opcodes.ILOAD, 1)
        val label = org.objectweb.asm.Label()
        mv?.visitJumpInsn(Opcodes.IFGE, label)
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.IADD) // line 3: ARITHMETIC mutation
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitLabel(label)
        mv?.visitInsn(Opcodes.ICONST_0)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 2)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithClassLevelSuppression(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        // Class-level @SuppressMutations with no operators — suppresses all
        val av = cw.visitAnnotation("Lcom/github/rodrigotimoteo/mutation/annotation/SuppressMutations;", true)
        av?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, org.objectweb.asm.Label())
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithOperatorSuppression(operators: List<String>): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val av = cw.visitAnnotation("Lcom/github/rodrigotimoteo/mutation/annotation/SuppressMutations;", true)
        if (operators.isNotEmpty()) {
            val arr = av?.visitArray("operators")
            for (op in operators) {
                arr?.visit(null, op)
            }
            arr?.visitEnd()
        }
        av?.visitEnd()
        // Include both ARITHMETIC (IADD) and CONDITIONALS_BOUNDARY (IFGE) in the class
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "(I)I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, org.objectweb.asm.Label())
        mv?.visitVarInsn(Opcodes.ILOAD, 1)
        val label = org.objectweb.asm.Label()
        mv?.visitJumpInsn(Opcodes.IFGE, label)
        mv?.visitInsn(Opcodes.ICONST_1)
        mv?.visitInsn(Opcodes.ICONST_2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitLabel(label)
        mv?.visitInsn(Opcodes.ICONST_0)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 2)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithMethodLevelSuppression(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()

        // Method "add" with @SuppressMutations (no operators → suppress all)
        val mv1 = cw.visitMethod(Opcodes.ACC_PUBLIC, "add", "()I", null, null)
        val mav1 =
            mv1?.visitAnnotation(
                "Lcom/github/rodrigotimoteo/mutation/annotation/SuppressMutations;",
                true,
            )
        mav1?.visitEnd()
        mv1?.visitCode()
        mv1?.visitLineNumber(1, org.objectweb.asm.Label())
        mv1?.visitInsn(Opcodes.ICONST_1)
        mv1?.visitInsn(Opcodes.ICONST_2)
        mv1?.visitInsn(Opcodes.IADD)
        mv1?.visitInsn(Opcodes.IRETURN)
        mv1?.visitMaxs(2, 1)
        mv1?.visitEnd()

        // Method "sub" without suppression
        val mv2 = cw.visitMethod(Opcodes.ACC_PUBLIC, "sub", "()I", null, null)
        mv2?.visitCode()
        mv2?.visitLineNumber(1, org.objectweb.asm.Label())
        mv2?.visitInsn(Opcodes.ICONST_5)
        mv2?.visitInsn(Opcodes.ICONST_3)
        mv2?.visitInsn(Opcodes.ISUB)
        mv2?.visitInsn(Opcodes.IRETURN)
        mv2?.visitMaxs(2, 1)
        mv2?.visitEnd()

        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithMethodOperatorSuppression(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()

        // Method "mixed" with @SuppressMutations(operators=["ARITHMETIC"])
        val mv1 = cw.visitMethod(Opcodes.ACC_PUBLIC, "mixed", "(I)I", null, null)
        val mav1 =
            mv1?.visitAnnotation(
                "Lcom/github/rodrigotimoteo/mutation/annotation/SuppressMutations;",
                true,
            )
        val arr = mav1?.visitArray("operators")
        arr?.visit(null, "ARITHMETIC")
        arr?.visitEnd()
        mav1?.visitEnd()
        mv1?.visitCode()
        mv1?.visitLineNumber(1, org.objectweb.asm.Label())
        mv1?.visitVarInsn(Opcodes.ILOAD, 1)
        val label = org.objectweb.asm.Label()
        mv1?.visitJumpInsn(Opcodes.IFGE, label)
        mv1?.visitInsn(Opcodes.ICONST_1)
        mv1?.visitInsn(Opcodes.ICONST_2)
        mv1?.visitInsn(Opcodes.IADD) // ARITHMETIC — should be suppressed
        mv1?.visitInsn(Opcodes.IRETURN)
        mv1?.visitLabel(label)
        mv1?.visitInsn(Opcodes.ICONST_0)
        mv1?.visitInsn(Opcodes.IRETURN)
        mv1?.visitMaxs(2, 2)
        mv1?.visitEnd()

        // Method "add" without suppression (still has ARITHMETIC)
        val mv2 = cw.visitMethod(Opcodes.ACC_PUBLIC, "add", "()I", null, null)
        mv2?.visitCode()
        mv2?.visitLineNumber(1, org.objectweb.asm.Label())
        mv2?.visitInsn(Opcodes.ICONST_1)
        mv2?.visitInsn(Opcodes.ICONST_2)
        mv2?.visitInsn(Opcodes.IADD)
        mv2?.visitInsn(Opcodes.IRETURN)
        mv2?.visitMaxs(2, 1)
        mv2?.visitEnd()

        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithCombinedSuppression(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Foo", null, "java/lang/Object", null)
        // Class-level: suppress RETURN_VALS
        val cav =
            cw.visitAnnotation(
                "Lcom/github/rodrigotimoteo/mutation/annotation/SuppressMutations;",
                true,
            )
        val carr = cav?.visitArray("operators")
        carr?.visit(null, "RETURN_VALS")
        carr?.visitEnd()
        cav?.visitEnd()

        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()

        // Method "add" with method-level @SuppressMutations(operators=["ARITHMETIC"])
        val mv1 = cw.visitMethod(Opcodes.ACC_PUBLIC, "add", "()I", null, null)
        val mav1 =
            mv1?.visitAnnotation(
                "Lcom/github/rodrigotimoteo/mutation/annotation/SuppressMutations;",
                true,
            )
        val marr = mav1?.visitArray("operators")
        marr?.visit(null, "ARITHMETIC")
        marr?.visitEnd()
        mav1?.visitEnd()
        mv1?.visitCode()
        mv1?.visitLineNumber(1, org.objectweb.asm.Label())
        mv1?.visitInsn(Opcodes.ICONST_1)
        mv1?.visitInsn(Opcodes.ICONST_2)
        mv1?.visitInsn(Opcodes.IADD)
        mv1?.visitInsn(Opcodes.IRETURN) // RETURN_VALS suppressed by class-level
        mv1?.visitMaxs(2, 1)
        mv1?.visitEnd()

        // Method "sub" without method-level suppression
        val mv2 = cw.visitMethod(Opcodes.ACC_PUBLIC, "sub", "()I", null, null)
        mv2?.visitCode()
        mv2?.visitLineNumber(1, org.objectweb.asm.Label())
        mv2?.visitInsn(Opcodes.ICONST_5)
        mv2?.visitInsn(Opcodes.ICONST_3)
        mv2?.visitInsn(Opcodes.ISUB)
        mv2?.visitInsn(Opcodes.IRETURN) // RETURN_VALS suppressed by class-level
        mv2?.visitMaxs(2, 1)
        mv2?.visitEnd()

        cw.visitEnd()
        return cw.toByteArray()
    }
}
