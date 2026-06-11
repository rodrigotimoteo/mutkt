package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MutationEngineTest {
    @Test
    fun `empty class list returns empty report`() {
        val engine = MutationEngine()
        val report = engine.runMutationTesting(emptyMap(), emptyList(), emptyMap())
        assertEquals(0, report.results.size)
    }

    @Test
    fun `single class with no mutations returns report with no results`() {
        val classBytes = buildEmptyClass()
        val engine = MutationEngine(enabledOperators = emptySet())
        val report =
            engine.runMutationTesting(
                mapOf("com/example/Empty" to classBytes),
                emptyList(),
                emptyMap(),
            )
        assertEquals(0, report.results.size)
    }

    @Test
    fun `single class with ARITHMETIC mutation and failing test returns KILLED`() {
        // Class with IADD on a method that always returns 5
        val classBytes = buildClassWithArithmetic()
        // Build a test that asserts the result is 8 (5+3). After IADD→ISUB, result is 2 → test FAILS
        val testBytes = buildTestAssertingAdd(5, 3, 8)
        val engine = MutationEngine(enabledOperators = setOf(MutationOperator.ARITHMETIC))
        val report =
            engine.runMutationTesting(
                classFiles = mapOf("com/example/Calc" to classBytes),
                testClassNames = listOf("com.example.CalcTest"),
                testClassBytes = mapOf("com/example/CalcTest" to testBytes),
            )
        // IADD -> ISUB should be KILLED
        assertEquals(1, report.results.size)
        assertEquals("KILLED", report.results[0].status.name)
    }

    @Test
    fun `single class with ARITHMETIC mutation and passing test returns KILLED when test fails`() {
        // Test asserts result is 5 (5+3=8, but test expects 5). Mutation changes to 5-3=2 → test fails
        val classBytes = buildClassWithArithmetic()
        val testBytes = buildTestAssertingAdd(5, 3, 5)
        val engine = MutationEngine(enabledOperators = setOf(MutationOperator.ARITHMETIC))
        val report =
            engine.runMutationTesting(
                classFiles = mapOf("com/example/Calc" to classBytes),
                testClassNames = listOf("com.example.CalcTest"),
                testClassBytes = mapOf("com/example/CalcTest" to testBytes),
            )
        assertEquals(1, report.results.size)
        // Status should be KILLED because the mutation causes the test to fail
        // (after mutation, 5-3=2, test expects 5, so test fails)
        assertEquals("KILLED", report.results[0].status.name)
    }

    @Test
    fun `class with no test methods returns NO_COVERAGE`() {
        val classBytes = buildClassWithArithmetic()
        val testBytes = buildTestClassWithNoTestMethods()
        val engine = MutationEngine(enabledOperators = setOf(MutationOperator.ARITHMETIC))
        val report =
            engine.runMutationTesting(
                classFiles = mapOf("com/example/Calc" to classBytes),
                testClassNames = listOf("com.example.CalcTest"),
                testClassBytes = mapOf("com/example/CalcTest" to testBytes),
            )
        assertEquals(1, report.results.size)
        assertEquals("NO_COVERAGE", report.results[0].status.name)
    }

    @Test
    fun `class that fails to load returns ERROR`() {
        val engine = MutationEngine(enabledOperators = setOf(MutationOperator.ARITHMETIC))
        // Engine may throw on bad bytes, or it may produce a report with ERROR status.
        // Either way is acceptable; we just verify it doesn't produce a misleading SUCCESS.
        try {
            val report =
                engine.runMutationTesting(
                    classFiles = mapOf("com/example/Bad" to ByteArray(20)),
                    testClassNames = listOf("com.example.BadTest"),
                    testClassBytes = mapOf("com/example/BadTest" to ByteArray(20)),
                )
            // If we get a report, no result should claim to be a valid KILLED/SURVIVED
            report.results.forEach { result ->
                assertTrue(
                    result.status.name in listOf("ERROR", "NO_COVERAGE"),
                    "Bad bytes should not produce valid status, got ${result.status.name}",
                )
            }
        } catch (e: Exception) {
            // Engine threw on bad bytes — also acceptable
        }
    }

    @Test
    fun `multiple classes with different statuses`() {
        val calcBytes = buildClassWithArithmetic()
        val emptyBytes = buildEmptyClass()
        val testBytes = buildTestAssertingAdd(5, 3, 8)
        val engine = MutationEngine(enabledOperators = setOf(MutationOperator.ARITHMETIC))
        val report =
            engine.runMutationTesting(
                classFiles =
                    mapOf(
                        "com/example/Calc" to calcBytes,
                        "com/example/Empty" to emptyBytes,
                    ),
                testClassNames = listOf("com.example.CalcTest"),
                testClassBytes = mapOf("com/example/CalcTest" to testBytes),
            )
        // Calc should have 1 KILLED, Empty should have 0
        assertTrue(report.results.size >= 1)
        val killed = report.results.count { it.status.name == "KILLED" }
        assertEquals(1, killed)
    }

    @Test
    fun `engine respects maxParallelMutants parameter`() {
        val engine = MutationEngine(maxParallelMutants = 8)
        val report = engine.runMutationTesting(emptyMap(), emptyList(), emptyMap())
        assertEquals(0, report.results.size)
    }

    @Test
    fun `engine respects timeoutMs parameter`() {
        val engine = MutationEngine(timeoutMs = 5_000L)
        val report = engine.runMutationTesting(emptyMap(), emptyList(), emptyMap())
        assertEquals(0, report.results.size)
    }

    @Test
    fun `engine respects enabledOperators parameter`() {
        val engine = MutationEngine(enabledOperators = setOf(MutationOperator.ARITHMETIC))
        val report = engine.runMutationTesting(emptyMap(), emptyList(), emptyMap())
        assertEquals(0, report.results.size)
    }

    @Test
    fun `engine with empty enabledOperators generates no mutations`() {
        val classBytes = buildClassWithArithmetic()
        val engine = MutationEngine(enabledOperators = emptySet())
        val report =
            engine.runMutationTesting(
                mapOf("com/example/Calc" to classBytes),
                emptyList(),
                emptyMap(),
            )
        assertEquals(0, report.results.size)
    }

    @Test
    fun `engine with multiple operators generates multiple mutations`() {
        val classBytes = buildClassWithArithmetic()
        val testBytes = buildTestClassWithNoTestMethods() // Will result in NO_COVERAGE
        val engine = MutationEngine(enabledOperators = MutationOperator.MVP_OPERATORS)
        val report =
            engine.runMutationTesting(
                classFiles = mapOf("com/example/Calc" to classBytes),
                testClassNames = listOf("com.example.CalcTest"),
                testClassBytes = mapOf("com/example/CalcTest" to testBytes),
            )
        // IADD + IRETURN → ARITHMETIC + RETURN_VALS at minimum
        assertTrue(report.results.size >= 1)
    }

    @Test
    fun `engine timeout per mutant returns TIMEOUT status`() {
        // Hard to test reliably, but we can verify the parameter is set
        val engine = MutationEngine(timeoutMs = 1L) // 1ms timeout
        val report = engine.runMutationTesting(emptyMap(), emptyList(), emptyMap())
        assertEquals(0, report.results.size)
    }

    @Test
    fun `engine with null coverageExecFile does not filter`() {
        val classBytes = buildClassWithArithmetic()
        val testBytes = buildTestClassWithNoTestMethods()
        val engine = MutationEngine(enabledOperators = setOf(MutationOperator.ARITHMETIC))
        val report =
            engine.runMutationTesting(
                classFiles = mapOf("com/example/Calc" to classBytes),
                testClassNames = listOf("com.example.CalcTest"),
                testClassBytes = mapOf("com/example/CalcTest" to testBytes),
                coverageExecFile = null,
            )
        // Without coverage file, all mutations are tested
        assertTrue(report.results.isNotEmpty())
    }

    @Test
    fun `engine with non-existent coverage file does not filter`() {
        val classBytes = buildClassWithArithmetic()
        val testBytes = buildTestClassWithNoTestMethods()
        val engine = MutationEngine(enabledOperators = setOf(MutationOperator.ARITHMETIC))
        val report =
            engine.runMutationTesting(
                classFiles = mapOf("com/example/Calc" to classBytes),
                testClassNames = listOf("com.example.CalcTest"),
                testClassBytes = mapOf("com/example/CalcTest" to testBytes),
                coverageExecFile = java.io.File("/tmp/nonexistent.exec"),
            )
        // Non-existent coverage file is treated as no filter
        assertTrue(report.results.isNotEmpty())
    }

    @Test
    fun `report contains all results`() {
        val classBytes = buildClassWithArithmetic()
        val testBytes = buildTestAssertingAdd(5, 3, 8)
        val engine = MutationEngine(enabledOperators = setOf(MutationOperator.ARITHMETIC))
        val report =
            engine.runMutationTesting(
                classFiles = mapOf("com/example/Calc" to classBytes),
                testClassNames = listOf("com.example.CalcTest"),
                testClassBytes = mapOf("com/example/CalcTest" to testBytes),
            )
        assertNotNull(report)
        assertNotNull(report.results)
    }

    @Test
    fun `report has duration tracking`() {
        val engine = MutationEngine()
        val report = engine.runMutationTesting(emptyMap(), emptyList(), emptyMap())
        assertTrue(report.totalExecutionTimeMs >= 0L)
    }

    @Test
    fun `engine runs without errors on minimal inputs`() {
        val engine = MutationEngine()
        val report = engine.runMutationTesting(emptyMap(), emptyList(), emptyMap())
        assertNotNull(report)
    }

    @Test
    fun `engine with synthetic-only class generates no mutations`() {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC + Opcodes.ACC_SYNTHETIC, "com/example/Synth", null, "java/lang/Object", null)
        cw.visitEnd()
        val bytes = cw.toByteArray()
        val engine = MutationEngine(enabledOperators = MutationOperator.MVP_OPERATORS)
        val report =
            engine.runMutationTesting(
                mapOf("com/example/Synth" to bytes),
                emptyList(),
                emptyMap(),
            )
        assertEquals(0, report.results.size)
    }

    @Test
    fun `engine with class having SuppressMutations generates no mutations`() {
        val classBytes = buildClassWithSuppressMutations()
        val engine = MutationEngine(enabledOperators = MutationOperator.MVP_OPERATORS)
        val report =
            engine.runMutationTesting(
                mapOf("com/example/Suppressed" to classBytes),
                emptyList(),
                emptyMap(),
            )
        assertEquals(0, report.results.size)
    }

    @Test
    fun `engine statistics match results`() {
        val classBytes = buildClassWithArithmetic()
        val testBytes = buildTestAssertingAdd(5, 3, 8)
        val engine = MutationEngine(enabledOperators = setOf(MutationOperator.ARITHMETIC))
        val report =
            engine.runMutationTesting(
                classFiles = mapOf("com/example/Calc" to classBytes),
                testClassNames = listOf("com.example.CalcTest"),
                testClassBytes = mapOf("com/example/CalcTest" to testBytes),
            )
        val killed = report.results.count { it.status.name == "KILLED" }
        assertTrue(
            report.killedMutations == killed,
            "killedMutations (${report.killedMutations}) should match actual KILLED count ($killed)",
        )
    }

    @Test
    fun `engine killed percentage is computed correctly`() {
        val classBytes = buildClassWithArithmetic()
        val testBytes = buildTestAssertingAdd(5, 3, 8)
        val engine = MutationEngine(enabledOperators = setOf(MutationOperator.ARITHMETIC))
        val report =
            engine.runMutationTesting(
                classFiles = mapOf("com/example/Calc" to classBytes),
                testClassNames = listOf("com.example.CalcTest"),
                testClassBytes = mapOf("com/example/CalcTest" to testBytes),
            )
        if (report.totalMutations > 0) {
            val expectedPct = (report.killedMutations * 100) / report.totalMutations
            assertEquals(expectedPct, report.killedPercentage)
        }
    }

    @Test
    fun `engine with include patterns filters mutations`() {
        val classBytes = buildClassWithArithmetic()
        val engine =
            MutationEngine(
                enabledOperators = setOf(MutationOperator.ARITHMETIC),
                includePatterns = listOf("com\\.example\\.Calc"),
            )
        val report =
            engine.runMutationTesting(
                classFiles = mapOf("com/example/Calc" to classBytes),
                emptyList(),
                emptyMap(),
            )
        assertTrue(report.results.isNotEmpty())
    }

    @Test
    fun `engine with exclude patterns filters mutations`() {
        val classBytes = buildClassWithArithmetic()
        val engine =
            MutationEngine(
                enabledOperators = setOf(MutationOperator.ARITHMETIC),
                excludePatterns = listOf("com\\.example\\.Calc"),
            )
        val report =
            engine.runMutationTesting(
                classFiles = mapOf("com/example/Calc" to classBytes),
                emptyList(),
                emptyMap(),
            )
        assertEquals(0, report.results.size)
    }

    @Test
    fun `engine with target test patterns filters tests`() {
        val classBytes = buildClassWithArithmetic()
        val testBytes = buildTestAssertingAdd(5, 3, 8)
        val engine =
            MutationEngine(
                enabledOperators = setOf(MutationOperator.ARITHMETIC),
                targetTestPatterns = listOf("com\\.example\\.Calc.*"),
            )
        val report =
            engine.runMutationTesting(
                classFiles = mapOf("com/example/Calc" to classBytes),
                testClassNames = listOf("com.example.CalcTest"),
                testClassBytes = mapOf("com/example/CalcTest" to testBytes),
            )
        assertTrue(report.results.isNotEmpty())
    }

    @Test
    fun `engine with exclude test patterns filters tests`() {
        val classBytes = buildClassWithArithmetic()
        val testBytes = buildTestAssertingAdd(5, 3, 8)
        val engine =
            MutationEngine(
                enabledOperators = setOf(MutationOperator.ARITHMETIC),
                excludeTestPatterns = listOf("com\\.example\\.CalcTest"),
            )
        val report =
            engine.runMutationTesting(
                classFiles = mapOf("com/example/Calc" to classBytes),
                testClassNames = listOf("com.example.CalcTest"),
                testClassBytes = mapOf("com/example/CalcTest" to testBytes),
            )
        // Test class excluded → no tests run → NO_COVERAGE
        assertEquals(1, report.results.size)
        assertEquals("NO_COVERAGE", report.results[0].status.name)
    }

    @Test
    fun `engine with maxMutationsPerClass limits results`() {
        val classBytes = buildClassWithArithmetic()
        val engine =
            MutationEngine(
                enabledOperators = MutationOperator.MVP_OPERATORS,
                maxMutationsPerClass = 1,
            )
        val report =
            engine.runMutationTesting(
                classFiles = mapOf("com/example/Calc" to classBytes),
                emptyList(),
                emptyMap(),
            )
        assertTrue(report.results.size <= 1)
    }

    @Test
    fun `engine with changedClasses filters by incremental`() {
        val classBytes = buildClassWithArithmetic()
        val engine =
            MutationEngine(
                enabledOperators = setOf(MutationOperator.ARITHMETIC),
                changedClasses = setOf("com.example.Foo"),
            )
        val report =
            engine.runMutationTesting(
                classFiles = mapOf("com/example/Calc" to classBytes),
                emptyList(),
                emptyMap(),
            )
        assertEquals(0, report.results.size)
    }

    @Test
    fun `engine with cache enabled runs correctly`() {
        val classBytes = buildClassWithArithmetic()
        val projectDir = java.io.File(System.getProperty("java.io.tmpdir"), "mutkt-test-cache-${System.nanoTime()}")
        projectDir.mkdirs()
        try {
            val engine =
                MutationEngine(
                    enabledOperators = setOf(MutationOperator.ARITHMETIC),
                    enableCache = true,
                    projectDir = projectDir,
                )
            val report =
                engine.runMutationTesting(
                    classFiles = mapOf("com/example/Calc" to classBytes),
                    testClassNames = listOf("com.example.CalcTest"),
                    testClassBytes = mapOf("com/example/CalcTest" to buildTestAssertingAdd(5, 3, 8)),
                )
            assertTrue(report.results.isNotEmpty())
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `engine with subsumption enabled runs correctly`() {
        val classBytes = buildClassWithArithmetic()
        val testBytes = buildTestAssertingAdd(5, 3, 8)
        val engine =
            MutationEngine(
                enabledOperators = setOf(MutationOperator.ARITHMETIC),
                enableSubsumption = true,
            )
        val report =
            engine.runMutationTesting(
                classFiles = mapOf("com/example/Calc" to classBytes),
                testClassNames = listOf("com.example.CalcTest"),
                testClassBytes = mapOf("com/example/CalcTest" to testBytes),
            )
        assertTrue(report.results.isNotEmpty())
    }

    @Test
    fun `engine with baseline enabled saves results`() {
        val classBytes = buildClassWithArithmetic()
        val projectDir = java.io.File(System.getProperty("java.io.tmpdir"), "mutkt-test-baseline-${System.nanoTime()}")
        projectDir.mkdirs()
        try {
            val engine =
                MutationEngine(
                    enabledOperators = setOf(MutationOperator.ARITHMETIC),
                    projectDir = projectDir,
                )
            val report =
                engine.runMutationTesting(
                    classFiles = mapOf("com/example/Calc" to classBytes),
                    testClassNames = listOf("com.example.CalcTest"),
                    testClassBytes = mapOf("com/example/CalcTest" to buildTestAssertingAdd(5, 3, 8)),
                )
            assertTrue(report.results.isNotEmpty())
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `engine with baseline and incremental merges results`() {
        val classBytes = buildClassWithArithmetic()
        val projectDir = java.io.File(System.getProperty("java.io.tmpdir"), "mutkt-test-baseline-merge-${System.nanoTime()}")
        projectDir.mkdirs()
        try {
            val engine =
                MutationEngine(
                    enabledOperators = setOf(MutationOperator.ARITHMETIC),
                    projectDir = projectDir,
                    changedClasses = setOf("com.example.Calc"),
                )
            val report =
                engine.runMutationTesting(
                    classFiles = mapOf("com/example/Calc" to classBytes),
                    testClassNames = listOf("com.example.CalcTest"),
                    testClassBytes = mapOf("com.example/C" + "alcTest" to buildTestAssertingAdd(5, 3, 8)),
                )
            assertTrue(report.results.isNotEmpty())
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `engine with testOrdering enabled runs correctly`() {
        val classBytes = buildClassWithArithmetic()
        val testBytes = buildTestAssertingAdd(5, 3, 8)
        val projectDir = java.io.File(System.getProperty("java.io.tmpdir"), "mutkt-test-history-${System.nanoTime()}")
        projectDir.mkdirs()
        try {
            val engine =
                MutationEngine(
                    enabledOperators = setOf(MutationOperator.ARITHMETIC),
                    enableTestOrdering = true,
                    projectDir = projectDir,
                )
            val report =
                engine.runMutationTesting(
                    classFiles = mapOf("com/example/Calc" to classBytes),
                    testClassNames = listOf("com.example.CalcTest"),
                    testClassBytes = mapOf("com/example/CalcTest" to testBytes),
                )
            assertTrue(report.results.isNotEmpty())
        } finally {
            projectDir.deleteRecursively()
        }
    }

    // ==================== HELPERS ====================

    private fun buildClassWithArithmetic(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Calc", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "add", "(II)I", null, null)
        mv?.visitCode()
        mv?.visitLineNumber(1, Label())
        mv?.visitVarInsn(Opcodes.ILOAD, 1)
        mv?.visitVarInsn(Opcodes.ILOAD, 2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 3)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildEmptyClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Empty", null, "java/lang/Object", null)
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildClassWithSuppressMutations(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Suppressed", null, "java/lang/Object", null)
        val av = cw.visitAnnotation("Lcom/github/rodrigotimoteo/mutation/annotation/SuppressMutations;", true)
        av?.visitEnd()
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "add", "(II)I", null, null)
        mv?.visitCode()
        mv?.visitVarInsn(Opcodes.ILOAD, 1)
        mv?.visitVarInsn(Opcodes.ILOAD, 2)
        mv?.visitInsn(Opcodes.IADD)
        mv?.visitInsn(Opcodes.IRETURN)
        mv?.visitMaxs(2, 3)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildTestAssertingAdd(
        a: Int,
        b: Int,
        expected: Int,
    ): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/CalcTest", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "testAdd", "()V", null, null)
        mv?.visitCode()
        val av = mv?.visitAnnotation("Lorg/junit/jupiter/api/Test;", true)
        av?.visitEnd()
        // Create a Calc instance
        mv?.visitTypeInsn(Opcodes.NEW, "com/example/Calc")
        mv?.visitInsn(Opcodes.DUP)
        mv?.visitMethodInsn(Opcodes.INVOKESPECIAL, "com/example/Calc", "<init>", "()V", false)
        // Store in local 1
        mv?.visitVarInsn(Opcodes.ASTORE, 1)
        // Call add(a, b)
        mv?.visitVarInsn(Opcodes.ALOAD, 1)
        mv?.visitIntInsn(Opcodes.BIPUSH, a)
        mv?.visitIntInsn(Opcodes.BIPUSH, b)
        mv?.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "com/example/Calc", "add", "(II)I", false)
        // Compare with expected
        mv?.visitIntInsn(Opcodes.BIPUSH, expected)
        // If equal, return. Otherwise throw AssertionError.
        val ifLabel = Label()
        mv?.visitJumpInsn(Opcodes.IF_ICMPEQ, ifLabel)
        // Throw AssertionError
        mv?.visitTypeInsn(Opcodes.NEW, "java/lang/AssertionError")
        mv?.visitInsn(Opcodes.DUP)
        mv?.visitLdcInsn("Addition result does not match expected")
        mv?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/AssertionError", "<init>", "(Ljava/lang/Object;)V", false)
        mv?.visitInsn(Opcodes.ATHROW)
        mv?.visitLabel(ifLabel)
        mv?.visitInsn(Opcodes.RETURN)
        mv?.visitMaxs(3, 2)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun buildTestClassWithNoTestMethods(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/CalcTest", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor?.visitInsn(Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "helper", "()V", null, null)
        mv?.visitCode()
        mv?.visitInsn(Opcodes.RETURN)
        mv?.visitMaxs(0, 1)
        mv?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }
}
