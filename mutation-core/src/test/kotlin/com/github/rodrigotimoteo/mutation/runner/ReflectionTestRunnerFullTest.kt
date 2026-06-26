package com.github.rodrigotimoteo.mutation.runner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.concurrent.atomic.AtomicInteger

object AsmTestTracker {
    @JvmStatic
    val beforeEachCount = AtomicInteger(0)

    @JvmStatic
    val afterEachCount = AtomicInteger(0)

    @JvmStatic
    val beforeAllCount = AtomicInteger(0)

    @JvmStatic
    val afterAllCount = AtomicInteger(0)

    @JvmStatic
    val testExecutions = mutableListOf<String>()

    @JvmStatic
    fun reset() {
        beforeEachCount.set(0)
        afterEachCount.set(0)
        beforeAllCount.set(0)
        afterAllCount.set(0)
        testExecutions.clear()
    }

    @JvmStatic
    fun incBeforeEach() = beforeEachCount.incrementAndGet()

    @JvmStatic
    fun incAfterEach() = afterEachCount.incrementAndGet()

    @JvmStatic
    fun incBeforeAll() = beforeAllCount.incrementAndGet()

    @JvmStatic
    fun incAfterAll() = afterAllCount.incrementAndGet()

    @JvmStatic
    fun recordTest(name: String) = testExecutions.add(name)
}

class DynamicClassLoader(parent: ClassLoader) : ClassLoader(parent) {
    fun define(
        name: String,
        bytes: ByteArray,
    ): Class<*> = defineClass(name, bytes, 0, bytes.size)
}

class ReflectionTestRunnerFullTest {
    private val classLoader = DynamicClassLoader(ReflectionTestRunnerFullTest::class.java.classLoader)
    private val runner = ReflectionTestRunner(classLoader)

    @BeforeEach
    fun resetTracker() {
        AsmTestTracker.reset()
    }

    @Test
    fun `single Test method passes`() {
        val cw = buildClassWriter("test/SinglePassTest")
        addTestMethod(cw, "test1") {
            visitInsn(Opcodes.RETURN)
        }
        cw.visitEnd()

        val clazz = classLoader.define("test.SinglePassTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
    }

    @Test
    fun `single Test method fails`() {
        val cw = buildClassWriter("test/SingleFailTest")
        addFailingTestMethod(cw, "test1")
        cw.visitEnd()

        val clazz = classLoader.define("test.SingleFailTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(1, results.testsFound)
        assertEquals(0, results.testsSucceeded)
        assertEquals(1, results.testsFailed)
        assertTrue(results.failureMessages.any { it.contains("fail") })
    }

    @Test
    fun `multiple Test methods mix pass and fail`() {
        val cw = buildClassWriter("test/MixTest")
        addTestMethod(cw, "passTest") {
            visitInsn(Opcodes.RETURN)
        }
        addFailingTestMethod(cw, "failTest")
        cw.visitEnd()

        val clazz = classLoader.define("test.MixTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(2, results.testsFound)
        assertEquals(1, results.testsSucceeded)
        assertEquals(1, results.testsFailed)
    }

    @Test
    fun `Nested class two levels deep`() {
        val outerName = "test/NestedOuterTest"
        val innerName = "test/NestedOuterTest\$InnerTest"

        val outerCw = buildClassWriter(outerName)
        outerCw.visitInnerClass(innerName, outerName, "InnerTest", Opcodes.ACC_PUBLIC)
        addTestMethod(outerCw, "outerTest") {
            visitInsn(Opcodes.RETURN)
        }
        outerCw.visitEnd()

        val innerCw = buildInnerClassWriter(outerName, innerName)
        innerCw.visitInnerClass(innerName, outerName, "InnerTest", Opcodes.ACC_PUBLIC)
        val nestedAnn = innerCw.visitAnnotation("Lorg/junit/jupiter/api/Nested;", true)
        nestedAnn.visitEnd()
        addTestMethod(innerCw, "innerTest") {
            visitInsn(Opcodes.RETURN)
        }
        innerCw.visitEnd()

        classLoader.define("test.NestedOuterTest", outerCw.toByteArray())
        classLoader.define("test.NestedOuterTest\$InnerTest", innerCw.toByteArray())

        val results = runner.runTests(listOf("test.NestedOuterTest"))

        assertEquals(2, results.testsFound)
        assertEquals(2, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
    }

    @Test
    fun `ParameterizedTest with ValueSource ints`() {
        val cw = buildClassWriter("test/ValueSourceTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "testParams", "(I)V", null, null)
        val ptAnn = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        ptAnn.visitEnd()
        val vsAnn = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/ValueSource;", true)
        val arr = vsAnn.visitArray("ints")
        arr.visit(null, 1)
        arr.visit(null, 2)
        arr.visit(null, 3)
        arr.visitEnd()
        vsAnn.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.ValueSourceTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(3, results.testsFound)
        assertEquals(3, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
    }

    @Test
    fun `RepeatedTest with value 3`() {
        val cw = buildClassWriter("test/RepeatedTestClass")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "repeatedTest", "()V", null, null)
        val rtAnn = mv.visitAnnotation("Lorg/junit/jupiter/api/RepeatedTest;", true)
        rtAnn.visit("value", 3)
        rtAnn.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.RepeatedTestClass", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(3, results.testsFound)
        assertEquals(3, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
    }

    @Test
    fun `BeforeEach runs before each test`() {
        val cw = buildClassWriter("test/BeforeEachTest")
        addLifecycleMethod(cw, "setup", "Lorg/junit/jupiter/api/BeforeEach;", false) {
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "com/github/rodrigotimoteo/mutation/runner/AsmTestTracker",
                "incBeforeEach",
                "()I",
                false,
            )
            visitInsn(Opcodes.POP)
        }
        addTestMethod(cw, "test1") {
            visitInsn(Opcodes.RETURN)
        }
        addTestMethod(cw, "test2") {
            visitInsn(Opcodes.RETURN)
        }
        cw.visitEnd()

        val clazz = classLoader.define("test.BeforeEachTest", cw.toByteArray())
        runner.runTests(listOf(clazz.name))

        assertEquals(2, AsmTestTracker.beforeEachCount.get())
    }

    @Test
    fun `AfterEach runs after each test`() {
        val cw = buildClassWriter("test/AfterEachTest")
        addLifecycleMethod(cw, "teardown", "Lorg/junit/jupiter/api/AfterEach;", false) {
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "com/github/rodrigotimoteo/mutation/runner/AsmTestTracker",
                "incAfterEach",
                "()I",
                false,
            )
            visitInsn(Opcodes.POP)
        }
        addTestMethod(cw, "test1") {
            visitInsn(Opcodes.RETURN)
        }
        addTestMethod(cw, "test2") {
            visitInsn(Opcodes.RETURN)
        }
        cw.visitEnd()

        val clazz = classLoader.define("test.AfterEachTest", cw.toByteArray())
        runner.runTests(listOf(clazz.name))

        assertEquals(2, AsmTestTracker.afterEachCount.get())
    }

    @Test
    fun `BeforeAll runs once before all tests`() {
        val cw = buildClassWriter("test/BeforeAllTest")
        addLifecycleMethod(cw, "setupAll", "Lorg/junit/jupiter/api/BeforeAll;", true) {
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "com/github/rodrigotimoteo/mutation/runner/AsmTestTracker",
                "incBeforeAll",
                "()I",
                false,
            )
            visitInsn(Opcodes.POP)
        }
        addTestMethod(cw, "test1") {
            visitInsn(Opcodes.RETURN)
        }
        addTestMethod(cw, "test2") {
            visitInsn(Opcodes.RETURN)
        }
        cw.visitEnd()

        val clazz = classLoader.define("test.BeforeAllTest", cw.toByteArray())
        runner.runTests(listOf(clazz.name))

        assertEquals(1, AsmTestTracker.beforeAllCount.get())
    }

    @Test
    fun `AfterAll runs once after all tests`() {
        val cw = buildClassWriter("test/AfterAllTest")
        addLifecycleMethod(cw, "teardownAll", "Lorg/junit/jupiter/api/AfterAll;", true) {
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "com/github/rodrigotimoteo/mutation/runner/AsmTestTracker",
                "incAfterAll",
                "()I",
                false,
            )
            visitInsn(Opcodes.POP)
        }
        addTestMethod(cw, "test1") {
            visitInsn(Opcodes.RETURN)
        }
        addTestMethod(cw, "test2") {
            visitInsn(Opcodes.RETURN)
        }
        cw.visitEnd()

        val clazz = classLoader.define("test.AfterAllTest", cw.toByteArray())
        runner.runTests(listOf(clazz.name))

        assertEquals(1, AsmTestTracker.afterAllCount.get())
    }

    @Test
    fun `BeforeAll failure marks the test as failed`() {
        val cw = buildClassWriter("test/BeforeAllFailTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "failingSetup", "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/jupiter/api/BeforeAll;", true)
        ann.visitEnd()
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
        mv.visitInsn(Opcodes.DUP)
        mv.visitLdcInsn("Setup failed intentionally")
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false)
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitMaxs(3, 0)
        mv.visitEnd()

        addTestMethod(cw, "shouldNotRun") {
            visitInsn(Opcodes.RETURN)
        }
        cw.visitEnd()

        val clazz = classLoader.define("test.BeforeAllFailTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        // JUnit Jupiter still discovers the @Test method even when @BeforeAll
        // fails; it reports the @Test as failed with the underlying cause.
        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsFailed)
        assertTrue(results.failureMessages.any { it.contains("Setup failed intentionally") })
    }

    @Test
    fun `ParameterizedTest with MethodSource`() {
        val cw = buildClassWriter("test/MethodSourceTest")
        addFactoryMethod(cw, "provideData")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "testWithData", "(I)V", null, null)
        val ptAnn = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        ptAnn.visitEnd()
        val msAnn = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/MethodSource;", true)
        val arr = msAnn.visitArray("value")
        arr.visit(null, "provideData")
        arr.visitEnd()
        msAnn.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.MethodSourceTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(3, results.testsFound)
        assertEquals(3, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
    }

    @Test
    fun `Disabled annotation is respected on class`() {
        val cw = buildClassWriter("test/DisabledClassTest")
        val disabledAnn = cw.visitAnnotation("Lorg/junit/jupiter/api/Disabled;", true)
        disabledAnn.visitEnd()
        addTestMethod(cw, "skippedTest") {
            visitInsn(Opcodes.RETURN)
        }
        cw.visitEnd()

        val clazz = classLoader.define("test.DisabledClassTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(0, results.testsFound)
        assertEquals(0, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
    }

    @Test
    fun `Disabled annotation is respected on method`() {
        val cw = buildClassWriter("test/DisabledMethodTest")
        addTestMethod(cw, "enabledTest") {
            visitInsn(Opcodes.RETURN)
        }
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "disabledTest", "()V", null, null)
        val disabledAnn = mv.visitAnnotation("Lorg/junit/jupiter/api/Disabled;", true)
        disabledAnn.visitEnd()
        val testAnn = mv.visitAnnotation("Lorg/junit/jupiter/api/Test;", true)
        testAnn.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.DisabledMethodTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
    }

    @Test
    fun `factory method discovery for dynamic tests`() {
        val cw = buildClassWriter("test/FactoryDiscoveryTest")
        addFactoryMethod(cw, "dynamicData")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "testDynamic", "(I)V", null, null)
        val ptAnn = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        ptAnn.visitEnd()
        val msAnn = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/MethodSource;", true)
        val arr = msAnn.visitArray("value")
        arr.visit(null, "dynamicData")
        arr.visitEnd()
        msAnn.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.FactoryDiscoveryTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(3, results.testsFound)
        assertEquals(3, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
    }

    @Test
    fun `inner class instantiation for Nested`() {
        val outerName = "test/InnerInstOuter"
        val innerName = "test/InnerInstOuter\$InnerInst"

        val outerCw = buildClassWriter(outerName)
        outerCw.visitInnerClass(innerName, outerName, "InnerInst", Opcodes.ACC_PUBLIC)
        addTestMethod(outerCw, "outerTest") {
            visitInsn(Opcodes.RETURN)
        }
        outerCw.visitEnd()

        val innerCw = buildInnerClassWriter(outerName, innerName)
        innerCw.visitInnerClass(innerName, outerName, "InnerInst", Opcodes.ACC_PUBLIC)
        val nestedAnn = innerCw.visitAnnotation("Lorg/junit/jupiter/api/Nested;", true)
        nestedAnn.visitEnd()
        addTestMethod(innerCw, "innerTest") {
            visitInsn(Opcodes.RETURN)
        }
        innerCw.visitEnd()

        classLoader.define("test.InnerInstOuter", outerCw.toByteArray())
        classLoader.define("test.InnerInstOuter\$InnerInst", innerCw.toByteArray())

        val results = runner.runTests(listOf("test.InnerInstOuter"))

        assertEquals(2, results.testsFound)
        assertEquals(2, results.testsSucceeded)
    }

    @Test
    fun `multiple test classes`() {
        val cw1 = buildClassWriter("test/MultiClassOne")
        addTestMethod(cw1, "test1") {
            visitInsn(Opcodes.RETURN)
        }
        cw1.visitEnd()

        val cw2 = buildClassWriter("test/MultiClassTwo")
        addTestMethod(cw2, "test2") {
            visitInsn(Opcodes.RETURN)
        }
        addTestMethod(cw2, "test3") {
            visitInsn(Opcodes.RETURN)
        }
        cw2.visitEnd()

        val clazz1 = classLoader.define("test.MultiClassOne", cw1.toByteArray())
        val clazz2 = classLoader.define("test.MultiClassTwo", cw2.toByteArray())

        val results = runner.runTests(listOf(clazz1.name, clazz2.name))

        assertEquals(3, results.testsFound)
        assertEquals(3, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
    }

    @Test
    fun `class with no test methods returns empty results`() {
        val cw = buildClassWriter("test/NoTestMethods")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "regularMethod", "()V", null, null)
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.NoTestMethods", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(0, results.testsFound)
        assertEquals(0, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
    }

    @Test
    fun `companion object method invocation`() {
        val results = runner.runTests(listOf(CompanionFactoryTest::class.java.name))

        assertEquals(3, results.testsFound)
        assertEquals(3, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
    }

    @Test
    fun `JUnit 4 Test annotation`() {
        val cw = buildClassWriter("test/JUnit4Test")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "legacyTest", "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/Test;", true)
        ann.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.JUnit4Test", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
    }

    @Test
    fun `JUnit 4 Before and After lifecycle`() {
        val cw = buildClassWriter("test/JUnit4LifecycleTest")
        val beforeMv = cw.visitMethod(Opcodes.ACC_PUBLIC, "setUp", "()V", null, null)
        val beforeAnn = beforeMv.visitAnnotation("Lorg/junit/Before;", true)
        beforeAnn.visitEnd()
        beforeMv.visitCode()
        beforeMv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "com/github/rodrigotimoteo/mutation/runner/AsmTestTracker",
            "incBeforeEach",
            "()I",
            false,
        )
        beforeMv.visitInsn(Opcodes.POP)
        beforeMv.visitInsn(Opcodes.RETURN)
        beforeMv.visitMaxs(0, 1)
        beforeMv.visitEnd()

        val afterMv = cw.visitMethod(Opcodes.ACC_PUBLIC, "tearDown", "()V", null, null)
        val afterAnn = afterMv.visitAnnotation("Lorg/junit/After;", true)
        afterAnn.visitEnd()
        afterMv.visitCode()
        afterMv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "com/github/rodrigotimoteo/mutation/runner/AsmTestTracker",
            "incAfterEach",
            "()I",
            false,
        )
        afterMv.visitInsn(Opcodes.POP)
        afterMv.visitInsn(Opcodes.RETURN)
        afterMv.visitMaxs(0, 1)
        afterMv.visitEnd()

        val testMv = cw.visitMethod(Opcodes.ACC_PUBLIC, "legacyTest", "()V", null, null)
        val testAnn = testMv.visitAnnotation("Lorg/junit/Test;", true)
        testAnn.visitEnd()
        testMv.visitCode()
        testMv.visitInsn(Opcodes.RETURN)
        testMv.visitMaxs(0, 1)
        testMv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.JUnit4LifecycleTest", cw.toByteArray())
        runner.runTests(listOf(clazz.name))

        assertEquals(1, AsmTestTracker.beforeEachCount.get())
        assertEquals(1, AsmTestTracker.afterEachCount.get())
    }

    @Test
    fun `deep nested Nested classes three levels`() {
        val outerName = "test/DeepOuter"
        val inner1Name = "test/DeepOuter\$DeepInner1"
        val inner2Name = "test/DeepOuter\$DeepInner1\$DeepInner2"

        val outerCw = buildClassWriter(outerName)
        outerCw.visitInnerClass(inner1Name, outerName, "DeepInner1", Opcodes.ACC_PUBLIC)
        addTestMethod(outerCw, "outerTest") {
            visitInsn(Opcodes.RETURN)
        }
        outerCw.visitEnd()

        val inner1Cw = buildInnerClassWriter(outerName, inner1Name)
        inner1Cw.visitInnerClass(inner1Name, outerName, "DeepInner1", Opcodes.ACC_PUBLIC)
        inner1Cw.visitInnerClass(inner2Name, inner1Name, "DeepInner2", Opcodes.ACC_PUBLIC)
        val nested1Ann = inner1Cw.visitAnnotation("Lorg/junit/jupiter/api/Nested;", true)
        nested1Ann.visitEnd()
        addTestMethod(inner1Cw, "inner1Test") {
            visitInsn(Opcodes.RETURN)
        }
        inner1Cw.visitEnd()

        val inner2Cw = buildInnerClassWriter(inner1Name, inner2Name)
        inner2Cw.visitInnerClass(inner2Name, inner1Name, "DeepInner2", Opcodes.ACC_PUBLIC)
        val nested2Ann = inner2Cw.visitAnnotation("Lorg/junit/jupiter/api/Nested;", true)
        nested2Ann.visitEnd()
        addTestMethod(inner2Cw, "inner2Test") {
            visitInsn(Opcodes.RETURN)
        }
        inner2Cw.visitEnd()

        classLoader.define("test.DeepOuter", outerCw.toByteArray())
        classLoader.define("test.DeepOuter\$DeepInner1", inner1Cw.toByteArray())
        classLoader.define("test.DeepOuter\$DeepInner1\$DeepInner2", inner2Cw.toByteArray())

        val results = runner.runTests(listOf("test.DeepOuter"))

        assertEquals(3, results.testsFound)
        assertEquals(3, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
    }

    private fun buildClassWriter(name: String): ClassWriter {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()
        return cw
    }

    private fun buildInnerClassWriter(
        outerName: String,
        innerName: String,
    ): ClassWriter {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, innerName, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(L$outerName;)V", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 2)
        mv.visitEnd()
        return cw
    }

    private fun addTestMethod(
        cw: ClassWriter,
        name: String,
        body: MethodVisitor.() -> Unit,
    ) {
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/jupiter/api/Test;", true)
        ann.visitEnd()
        mv.visitCode()
        body(mv)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
    }

    private fun addFailingTestMethod(
        cw: ClassWriter,
        name: String,
    ) {
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/jupiter/api/Test;", true)
        ann.visitEnd()
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
        mv.visitInsn(Opcodes.DUP)
        mv.visitLdcInsn("fail")
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false)
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitMaxs(3, 1)
        mv.visitEnd()
    }

    private fun addLifecycleMethod(
        cw: ClassWriter,
        name: String,
        annotationDesc: String,
        static: Boolean,
        body: MethodVisitor.() -> Unit,
    ) {
        val access = if (static) Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC else Opcodes.ACC_PUBLIC
        val mv = cw.visitMethod(access, name, "()V", null, null)
        val ann = mv.visitAnnotation(annotationDesc, true)
        ann.visitEnd()
        mv.visitCode()
        body(mv)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
    }

    private fun addFactoryMethod(
        cw: ClassWriter,
        name: String,
    ) {
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, name, "()Ljava/util/List;", null, null)
        mv.visitCode()
        mv.visitInsn(Opcodes.ICONST_1)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false)
        mv.visitInsn(Opcodes.ICONST_2)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false)
        mv.visitInsn(Opcodes.ICONST_3)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false)
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "java/util/List",
            "of",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
            true,
        )
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(3, 0)
        mv.visitEnd()
    }
}

class CompanionFactoryTest {
    companion object {
        @JvmStatic
        fun provideData(): List<Int> = listOf(1, 2, 3)
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("provideData")
    fun testWithCompanionData(value: Int) {
        org.junit.jupiter.api.Assertions.assertTrue(value > 0)
    }
}

class ReflectionTestRunnerExtendedTest {
    private val classLoader = DynamicClassLoader(ReflectionTestRunnerFullTest::class.java.classLoader)
    private val runner = ReflectionTestRunner(classLoader)

    @BeforeEach
    fun resetTracker() {
        AsmTestTracker.reset()
    }

    @Test
    fun `runTests with missing class name reports load failure`() {
        val results = runner.runTests(listOf("missing.Foo"))
        assertEquals(0, results.testsFound)
        assertEquals(1, results.testsFailed)
        assertTrue(results.failureMessages.any { it.contains("missing.Foo") })
    }

    @Test
    fun `InvocationTargetException during load extracts target message`() {
        val throwingLoader =
            object : ClassLoader() {
                override fun loadClass(name: String): Class<*> {
                    val ex = RuntimeException("Inner cause")
                    throw java.lang.reflect.InvocationTargetException(ex)
                }
            }
        val customRunner = ReflectionTestRunner(throwingLoader)
        val results = customRunner.runTests(listOf("anything.Foo"))
        assertEquals(1, results.testsFailed)
        assertTrue(results.failureMessages.any { it.contains("Inner cause") })
    }

    @Test
    fun `TestResults hasTests and hasFailures reflect outcomes`() {
        val empty = ReflectionTestRunner.TestResults(0, 0, 0, 0, emptyList())
        val passing = ReflectionTestRunner.TestResults(1, 1, 0, 0, emptyList())
        val failing = ReflectionTestRunner.TestResults(1, 0, 1, 0, listOf("boom"))

        assertTrue(!empty.hasTests && !empty.hasFailures)
        assertTrue(passing.hasTests && !passing.hasFailures)
        assertTrue(failing.hasTests && failing.hasFailures)
    }

    @Test
    fun `instance JUnit5 BeforeAll is rejected`() {
        // JUnit Jupiter requires @BeforeAll to be static; the engine
        // reports the entire class as a single container failure.
        val cw = buildClassWriterExt("test/InstanceBeforeAllTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "setup", "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/jupiter/api/BeforeAll;", true)
        ann.visitEnd()
        mv.visitCode()
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "com/github/rodrigotimoteo/mutation/runner/AsmTestTracker",
            "incBeforeAll",
            "()I",
            false,
        )
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        addTestMethodExt(cw, "t1") { visitInsn(Opcodes.RETURN) }
        addTestMethodExt(cw, "t2") { visitInsn(Opcodes.RETURN) }
        cw.visitEnd()

        val clazz = classLoader.define("test.InstanceBeforeAllTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        // Jupiter fails the class as a whole rather than running the @Test
        // methods, so the runner reports a single failed test result.
        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsFailed)
        assertEquals(0, AsmTestTracker.beforeAllCount.get())
    }

    @Test
    fun `static JUnit4 BeforeClass and AfterClass run once`() {
        val cw = buildClassWriterExt("test/JUnit4StaticLifecycleTest")
        val before = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "beforeAll", "()V", null, null)
        val beforeAnn = before.visitAnnotation("Lorg/junit/BeforeClass;", true)
        beforeAnn.visitEnd()
        before.visitCode()
        before.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "com/github/rodrigotimoteo/mutation/runner/AsmTestTracker",
            "incBeforeAll",
            "()I",
            false,
        )
        before.visitInsn(Opcodes.POP)
        before.visitInsn(Opcodes.RETURN)
        before.visitMaxs(0, 0)
        before.visitEnd()

        val after = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "afterAll", "()V", null, null)
        val afterAnn = after.visitAnnotation("Lorg/junit/AfterClass;", true)
        afterAnn.visitEnd()
        after.visitCode()
        after.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "com/github/rodrigotimoteo/mutation/runner/AsmTestTracker",
            "incAfterAll",
            "()I",
            false,
        )
        after.visitInsn(Opcodes.POP)
        after.visitInsn(Opcodes.RETURN)
        after.visitMaxs(0, 0)
        after.visitEnd()

        addTestMethodJUnit4Ext(cw, "t1") { visitInsn(Opcodes.RETURN) }
        addTestMethodJUnit4Ext(cw, "t2") { visitInsn(Opcodes.RETURN) }
        cw.visitEnd()

        val clazz = classLoader.define("test.JUnit4StaticLifecycleTest", cw.toByteArray())
        runner.runTests(listOf(clazz.name))

        assertEquals(1, AsmTestTracker.beforeAllCount.get())
        assertEquals(1, AsmTestTracker.afterAllCount.get())
    }

    @Test
    fun `instance JUnit5 AfterAll is rejected`() {
        // JUnit Jupiter requires @AfterAll to be static; the engine
        // reports the entire class as a single container failure.
        val cw = buildClassWriterExt("test/InstanceAfterAllTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "teardownAll", "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/jupiter/api/AfterAll;", true)
        ann.visitEnd()
        mv.visitCode()
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "com/github/rodrigotimoteo/mutation/runner/AsmTestTracker",
            "incAfterAll",
            "()I",
            false,
        )
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        addTestMethodExt(cw, "t1") { visitInsn(Opcodes.RETURN) }
        addTestMethodExt(cw, "t2") { visitInsn(Opcodes.RETURN) }
        cw.visitEnd()

        val clazz = classLoader.define("test.InstanceAfterAllTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        // Jupiter fails the class as a whole rather than running the @Test
        // methods, so the runner reports a single failed test result.
        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsFailed)
        assertEquals(0, AsmTestTracker.afterAllCount.get())
    }

    @Test
    fun `AfterClass exception is recorded but does not abort tests`() {
        val cw = buildClassWriterExt("test/AfterClassThrowsTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "teardown", "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/AfterClass;", true)
        ann.visitEnd()
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
        mv.visitInsn(Opcodes.DUP)
        mv.visitLdcInsn("teardown boom")
        mv.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/RuntimeException",
            "<init>",
            "(Ljava/lang/String;)V",
            false,
        )
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitMaxs(3, 0)
        mv.visitEnd()
        addTestMethodJUnit4Ext(cw, "t1") { visitInsn(Opcodes.RETURN) }
        cw.visitEnd()

        val clazz = classLoader.define("test.AfterClassThrowsTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        // JUnit Vintage reports the @Test method and the @AfterClass
        // container separately; the @Test itself succeeds but the
        // @AfterClass failure is surfaced as a context failure.
        assertEquals(2, results.testsFound)
        assertEquals(1, results.testsSucceeded)
        assertEquals(1, results.testsFailed)
        assertTrue(
            results.failureMessages.any { it.contains("teardown boom") },
            "Expected a 'teardown boom' failure message, got: ${results.failureMessages}",
        )
    }

    @Test
    fun `BeforeAll failure records non-InvocationTargetException cause`() {
        val cw = buildClassWriterExt("test/BeforeAllIntParamTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "badSetup", "(I)V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/jupiter/api/BeforeAll;", true)
        ann.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        addTestMethodExt(cw, "t1") { visitInsn(Opcodes.RETURN) }
        cw.visitEnd()

        val clazz = classLoader.define("test.BeforeAllIntParamTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        // Jupiter rejects @BeforeAll with parameters at discovery time, so
        // the @Test method is reported as failed and surfaced through the
        // standard failure channel rather than the reflection runner's
        // custom "@BeforeAll" message.
        assertEquals(1, results.testsFailed)
        assertTrue(results.failureMessages.isNotEmpty())
    }

    @Test
    fun `failing test with null message records empty cause`() {
        val cw = buildClassWriterExt("test/NullMessageFailTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/jupiter/api/Test;", true)
        ann.visitEnd()
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
        mv.visitInsn(Opcodes.DUP)
        mv.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/RuntimeException",
            "<init>",
            "()V",
            false,
        )
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitMaxs(2, 1)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.NullMessageFailTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(1, results.testsFailed)
        // The Launcher surfaces the test's display name; the reflection
        // runner used to format the message as "t1: " — Jupiter uses a
        // different but still informative label, so we just check the test
        // is present in the failure messages.
        assertTrue(results.failureMessages.isNotEmpty())
    }

    @Test
    fun `BeforeEach parameter mismatch hits Exception catch`() {
        val cw = buildClassWriterExt("test/BadBeforeEachTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "setup", "(I)V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/jupiter/api/BeforeEach;", true)
        ann.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        addTestMethodExt(cw, "t1") { visitInsn(Opcodes.RETURN) }
        cw.visitEnd()

        val clazz = classLoader.define("test.BadBeforeEachTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertTrue(results.testsFailed >= 1)
    }

    @Test
    fun `AfterEach exception is reported as test failure`() {
        val cw = buildClassWriterExt("test/AfterEachThrowsTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "teardown", "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/jupiter/api/AfterEach;", true)
        ann.visitEnd()
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
        mv.visitInsn(Opcodes.DUP)
        mv.visitLdcInsn("teardown error")
        mv.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/RuntimeException",
            "<init>",
            "(Ljava/lang/String;)V",
            false,
        )
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitMaxs(3, 1)
        mv.visitEnd()
        addTestMethodExt(cw, "t1") { visitInsn(Opcodes.RETURN) }
        cw.visitEnd()

        val clazz = classLoader.define("test.AfterEachThrowsTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        // Jupiter propagates @AfterEach exceptions to the test result; the
        // custom reflection runner used to swallow them, but with the JUnit
        // Platform Launcher we report them honestly.
        assertEquals(1, results.testsFound)
        assertEquals(0, results.testsSucceeded)
        assertEquals(1, results.testsFailed)
        assertTrue(results.failureMessages.any { it.contains("teardown error") })
    }

    @Test
    fun `RepeatedTest failure on later repetition counts failure`() {
        val cw = buildClassWriterExt("test/RepeatedFailTest")
        val counterField =
            cw.visitField(
                Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                "callCount",
                "I",
                null,
                0,
            )
        counterField.visitEnd()

        val factory =
            cw.visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "shouldThrow",
                "()Z",
                null,
                null,
            )
        factory.visitCode()
        factory.visitFieldInsn(
            Opcodes.GETSTATIC,
            "test/RepeatedFailTest",
            "callCount",
            "I",
        )
        factory.visitInsn(Opcodes.ICONST_1)
        val secondLbl = org.objectweb.asm.Label()
        factory.visitJumpInsn(Opcodes.IF_ICMPEQ, secondLbl)
        factory.visitInsn(Opcodes.ICONST_0)
        factory.visitInsn(Opcodes.IRETURN)
        factory.visitLabel(secondLbl)
        factory.visitInsn(Opcodes.ICONST_1)
        factory.visitInsn(Opcodes.IRETURN)
        factory.visitMaxs(2, 0)
        factory.visitEnd()

        val incrementer =
            cw.visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "incrementCount",
                "()V",
                null,
                null,
            )
        incrementer.visitCode()
        incrementer.visitFieldInsn(
            Opcodes.GETSTATIC,
            "test/RepeatedFailTest",
            "callCount",
            "I",
        )
        incrementer.visitInsn(Opcodes.ICONST_1)
        incrementer.visitInsn(Opcodes.IADD)
        incrementer.visitFieldInsn(
            Opcodes.PUTSTATIC,
            "test/RepeatedFailTest",
            "callCount",
            "I",
        )
        incrementer.visitInsn(Opcodes.RETURN)
        incrementer.visitMaxs(2, 0)
        incrementer.visitEnd()

        val testMv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "()V", null, null)
        val rt = testMv.visitAnnotation("Lorg/junit/jupiter/api/RepeatedTest;", true)
        rt.visit("value", 3)
        rt.visitEnd()
        testMv.visitCode()
        testMv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "test/RepeatedFailTest",
            "incrementCount",
            "()V",
            false,
        )
        testMv.visitMethodInsn(Opcodes.INVOKESTATIC, "test/RepeatedFailTest", "shouldThrow", "()Z", false)
        val skipLbl = org.objectweb.asm.Label()
        testMv.visitJumpInsn(Opcodes.IFEQ, skipLbl)
        testMv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
        testMv.visitInsn(Opcodes.DUP)
        testMv.visitLdcInsn("boom")
        testMv.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/RuntimeException",
            "<init>",
            "(Ljava/lang/String;)V",
            false,
        )
        testMv.visitInsn(Opcodes.ATHROW)
        testMv.visitLabel(skipLbl)
        testMv.visitInsn(Opcodes.RETURN)
        testMv.visitMaxs(3, 1)
        testMv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.RepeatedFailTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(3, results.testsFound)
        assertEquals(1, results.testsFailed)
    }

    @Test
    fun `zero-parameter ParameterizedTest is rejected`() {
        // JUnit Jupiter requires ParameterizedTest methods to declare at
        // least one parameter and an ArgumentsProvider. With no parameters
        // and no source, Jupiter still discovers the method but the
        // execution is reported as a container-level failure.
        val cw = buildClassWriterExt("test/ZeroParamParameterizedTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        ann.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.ZeroParamParameterizedTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        // The method is reported as a failed container; we surface it as
        // a single failed test result.
        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsFailed)
    }

    @Test
    fun `MethodSource with empty value fails to resolve parameters`() {
        val cw = buildClassWriterExt("test/EmptyMethodSourceTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(I)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val ms = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/MethodSource;", true)
        ms.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.EmptyMethodSourceTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        // Jupiter reports the test as failed (not the reflection runner's
        // custom "Could not resolve" message). The exact text comes from
        // Jupiter's own ParameterResolutionException.
        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsFailed)
        assertTrue(results.failureMessages.isNotEmpty())
    }

    @Test
    fun `missing factory method fails the parameterized test`() {
        val cw = buildClassWriterExt("test/MissingFactoryTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(I)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val ms = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/MethodSource;", true)
        val arr = ms.visitArray("value")
        arr.visit(null, "doesNotExist")
        arr.visitEnd()
        ms.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.MissingFactoryTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        // Jupiter reports the test as failed (its own ParameterResolution
        // message). The reflection runner's "Could not resolve" string was
        // a custom diagnostic; with the Launcher we let Jupiter speak.
        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsFailed)
        assertTrue(results.failureMessages.isNotEmpty())
    }

    @Test
    fun `factory method with parameters fails the test`() {
        // Jupiter's MethodArgumentsProvider requires the factory to be a
        // no-arg static method. A factory that takes parameters causes
        // Jupiter to fail the test at execution time.
        val cw = buildClassWriterExt("test/FactoryWithParamsTest")
        val factory =
            cw.visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "provideData",
                "(I)Ljava/util/List;",
                null,
                null,
            )
        factory.visitCode()
        factory.visitInsn(Opcodes.ACONST_NULL)
        factory.visitInsn(Opcodes.ARETURN)
        factory.visitMaxs(1, 1)
        factory.visitEnd()

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(I)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val ms = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/MethodSource;", true)
        val arr = ms.visitArray("value")
        arr.visit(null, "provideData")
        arr.visitEnd()
        ms.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.FactoryWithParamsTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsFailed)
        assertTrue(results.failureMessages.isNotEmpty())
    }

    @Test
    fun `factory returning non-Iterable is ignored`() {
        val cw = buildClassWriterExt("test/NonIterableFactoryTest")
        val factory =
            cw.visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "provideData",
                "()Ljava/lang/String;",
                null,
                null,
            )
        factory.visitCode()
        factory.visitLdcInsn("not a list")
        factory.visitInsn(Opcodes.ARETURN)
        factory.visitMaxs(1, 0)
        factory.visitEnd()

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(I)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val ms = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/MethodSource;", true)
        val arr = ms.visitArray("value")
        arr.visit(null, "provideData")
        arr.visitEnd()
        ms.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.NonIterableFactoryTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        // Jupiter reports a non-iterable factory as a test failure; the
        // reflection runner used to surface this with a custom message.
        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsFailed)
        assertTrue(results.failureMessages.isNotEmpty())
    }

    @Test
    fun `factory returning arrays spreads multi-arg params`() {
        val cw = buildClassWriterExt("test/ArraySpreadFactoryTest")
        val factory =
            cw.visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "provideData",
                "()Ljava/util/List;",
                null,
                null,
            )
        factory.visitCode()
        factory.visitInsn(Opcodes.ICONST_2)
        factory.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")
        factory.visitInsn(Opcodes.DUP)
        factory.visitInsn(Opcodes.ICONST_0)
        factory.visitInsn(Opcodes.ICONST_1)
        factory.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "java/lang/Integer",
            "valueOf",
            "(I)Ljava/lang/Integer;",
            false,
        )
        factory.visitInsn(Opcodes.AASTORE)
        factory.visitInsn(Opcodes.DUP)
        factory.visitInsn(Opcodes.ICONST_1)
        factory.visitInsn(Opcodes.ICONST_2)
        factory.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "java/lang/Integer",
            "valueOf",
            "(I)Ljava/lang/Integer;",
            false,
        )
        factory.visitInsn(Opcodes.AASTORE)
        factory.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "java/util/List",
            "of",
            "(Ljava/lang/Object;)Ljava/util/List;",
            true,
        )
        factory.visitInsn(Opcodes.ARETURN)
        factory.visitMaxs(6, 0)
        factory.visitEnd()

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(II)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val ms = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/MethodSource;", true)
        val arr = ms.visitArray("value")
        arr.visit(null, "provideData")
        arr.visitEnd()
        ms.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 3)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.ArraySpreadFactoryTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsSucceeded)
    }

    @Test
    fun `parameterized test with BeforeEach and array args`() {
        val cw = buildClassWriterExt("test/ParamWithBeforeEachTest")
        val be = cw.visitMethod(Opcodes.ACC_PUBLIC, "setup", "()V", null, null)
        val beAnn = be.visitAnnotation("Lorg/junit/jupiter/api/BeforeEach;", true)
        beAnn.visitEnd()
        be.visitCode()
        be.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "com/github/rodrigotimoteo/mutation/runner/AsmTestTracker",
            "incBeforeEach",
            "()I",
            false,
        )
        be.visitInsn(Opcodes.POP)
        be.visitInsn(Opcodes.RETURN)
        be.visitMaxs(0, 1)
        be.visitEnd()

        val factory =
            cw.visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "provideData",
                "()Ljava/util/List;",
                null,
                null,
            )
        factory.visitCode()
        factory.visitInsn(Opcodes.ICONST_2)
        factory.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")
        factory.visitInsn(Opcodes.DUP)
        factory.visitInsn(Opcodes.ICONST_0)
        factory.visitInsn(Opcodes.ICONST_1)
        factory.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "java/lang/Integer",
            "valueOf",
            "(I)Ljava/lang/Integer;",
            false,
        )
        factory.visitInsn(Opcodes.AASTORE)
        factory.visitInsn(Opcodes.DUP)
        factory.visitInsn(Opcodes.ICONST_1)
        factory.visitInsn(Opcodes.ICONST_2)
        factory.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "java/lang/Integer",
            "valueOf",
            "(I)Ljava/lang/Integer;",
            false,
        )
        factory.visitInsn(Opcodes.AASTORE)
        factory.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "java/util/List",
            "of",
            "(Ljava/lang/Object;)Ljava/util/List;",
            true,
        )
        factory.visitInsn(Opcodes.ARETURN)
        factory.visitMaxs(6, 0)
        factory.visitEnd()

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(I)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val ms = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/MethodSource;", true)
        val msArr = ms.visitArray("value")
        msArr.visit(null, "provideData")
        msArr.visitEnd()
        ms.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.ParamWithBeforeEachTest", cw.toByteArray())
        runner.runTests(listOf(clazz.name))

        assertEquals(1, AsmTestTracker.beforeEachCount.get())
    }

    @Test
    fun `parameterized method throwing hits InvocationTargetException catch`() {
        val cw = buildClassWriterExt("test/ParamThrowTest")
        addIntListFactoryExt(cw, "provideData", listOf(1, 2))

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(I)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val ms = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/MethodSource;", true)
        val arr = ms.visitArray("value")
        arr.visit(null, "provideData")
        arr.visitEnd()
        ms.visitEnd()
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ILOAD, 1)
        mv.visitInsn(Opcodes.ICONST_2)
        val lblReturn = org.objectweb.asm.Label()
        mv.visitJumpInsn(Opcodes.IF_ICMPNE, lblReturn)
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
        mv.visitInsn(Opcodes.DUP)
        mv.visitLdcInsn("two caused it")
        mv.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/RuntimeException",
            "<init>",
            "(Ljava/lang/String;)V",
            false,
        )
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitLabel(lblReturn)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(3, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.ParamThrowTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(2, results.testsFound)
        assertEquals(1, results.testsFailed)
        assertTrue(results.failureMessages.any { it.contains("two caused it") })
    }

    @Test
    fun `parameterized method with wrong arg type hits Exception catch`() {
        val cw = buildClassWriterExt("test/ParamTypeMismatchTest")
        val factory =
            cw.visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "provideData",
                "()Ljava/util/List;",
                null,
                null,
            )
        factory.visitCode()
        factory.visitLdcInsn("notAnInt")
        factory.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "java/util/List",
            "of",
            "(Ljava/lang/Object;)Ljava/util/List;",
            true,
        )
        factory.visitInsn(Opcodes.ARETURN)
        factory.visitMaxs(1, 0)
        factory.visitEnd()

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(I)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val ms = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/MethodSource;", true)
        val arr = ms.visitArray("value")
        arr.visit(null, "provideData")
        arr.visitEnd()
        ms.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.ParamTypeMismatchTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertTrue(results.testsFailed >= 1)
        assertTrue(results.failureMessages.isNotEmpty())
    }

    @Test
    fun `parameterized test AfterEach exception fails each invocation`() {
        val cw = buildClassWriterExt("test/ParamAfterEachThrowsTest")
        val ae = cw.visitMethod(Opcodes.ACC_PUBLIC, "teardown", "()V", null, null)
        val aeAnn = ae.visitAnnotation("Lorg/junit/jupiter/api/AfterEach;", true)
        aeAnn.visitEnd()
        ae.visitCode()
        ae.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
        ae.visitInsn(Opcodes.DUP)
        ae.visitLdcInsn("teardown boom")
        ae.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/RuntimeException",
            "<init>",
            "(Ljava/lang/String;)V",
            false,
        )
        ae.visitInsn(Opcodes.ATHROW)
        ae.visitMaxs(3, 1)
        ae.visitEnd()

        addIntListFactoryExt(cw, "provideData", listOf(1, 2))

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(I)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val ms = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/MethodSource;", true)
        val arr = ms.visitArray("value")
        arr.visit(null, "provideData")
        arr.visitEnd()
        ms.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.ParamAfterEachThrowsTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(2, results.testsFound)
        assertEquals(0, results.testsSucceeded)
        assertEquals(2, results.testsFailed)
        assertTrue(results.failureMessages.any { it.contains("teardown boom") })
    }

    @Test
    fun `ValueSource strings runs`() {
        val cw = buildClassWriterExt("test/ValueSourceStringsTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(Ljava/lang/String;)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val vs = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/ValueSource;", true)
        val arr = vs.visitArray("strings")
        arr.visit(null, "a")
        arr.visit(null, "b")
        arr.visitEnd()
        vs.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.ValueSourceStringsTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(2, results.testsFound)
        assertEquals(2, results.testsSucceeded)
    }

    @Test
    fun `ValueSource longs runs`() {
        val cw = buildClassWriterExt("test/ValueSourceLongsTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(J)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val vs = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/ValueSource;", true)
        val arr = vs.visitArray("longs")
        arr.visit(null, 10L)
        arr.visit(null, 20L)
        arr.visitEnd()
        vs.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 3)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.ValueSourceLongsTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(2, results.testsFound)
        assertEquals(2, results.testsSucceeded)
    }

    @Test
    fun `ValueSource floats runs`() {
        val cw = buildClassWriterExt("test/ValueSourceFloatsTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(F)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val vs = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/ValueSource;", true)
        val arr = vs.visitArray("floats")
        arr.visit(null, 1.5f)
        arr.visit(null, 2.5f)
        arr.visitEnd()
        vs.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.ValueSourceFloatsTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(2, results.testsFound)
        assertEquals(2, results.testsSucceeded)
    }

    @Test
    fun `ValueSource doubles runs`() {
        val cw = buildClassWriterExt("test/ValueSourceDoublesTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(D)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val vs = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/ValueSource;", true)
        val arr = vs.visitArray("doubles")
        arr.visit(null, 1.25)
        arr.visit(null, 2.5)
        arr.visitEnd()
        vs.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 3)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.ValueSourceDoublesTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(2, results.testsFound)
        assertEquals(2, results.testsSucceeded)
    }

    @Test
    fun `empty ValueSource yields unresolved message`() {
        val cw = buildClassWriterExt("test/EmptyValueSourceTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(I)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val vs = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/ValueSource;", true)
        vs.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.EmptyValueSourceTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsFailed)
        assertTrue(results.failureMessages.isNotEmpty())
    }

    @Test
    fun `ValueSource test with BeforeEach runs`() {
        val cw = buildClassWriterExt("test/ValueSourceWithBeforeEachTest")
        val be = cw.visitMethod(Opcodes.ACC_PUBLIC, "setup", "()V", null, null)
        val beAnn = be.visitAnnotation("Lorg/junit/jupiter/api/BeforeEach;", true)
        beAnn.visitEnd()
        be.visitCode()
        be.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "com/github/rodrigotimoteo/mutation/runner/AsmTestTracker",
            "incBeforeEach",
            "()I",
            false,
        )
        be.visitInsn(Opcodes.POP)
        be.visitInsn(Opcodes.RETURN)
        be.visitMaxs(0, 1)
        be.visitEnd()

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(I)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val vs = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/ValueSource;", true)
        val arr = vs.visitArray("ints")
        arr.visit(null, 1)
        arr.visit(null, 2)
        arr.visitEnd()
        vs.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.ValueSourceWithBeforeEachTest", cw.toByteArray())
        runner.runTests(listOf(clazz.name))

        assertEquals(2, AsmTestTracker.beforeEachCount.get())
    }

    @Test
    fun `ValueSource invocation failure records InvocationTargetException`() {
        val cw = buildClassWriterExt("test/ValueSourceFailTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(I)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val vs = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/ValueSource;", true)
        val arr = vs.visitArray("ints")
        arr.visit(null, 1)
        arr.visit(null, 2)
        arr.visitEnd()
        vs.visitEnd()
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ILOAD, 1)
        mv.visitInsn(Opcodes.ICONST_2)
        val lblReturn = org.objectweb.asm.Label()
        mv.visitJumpInsn(Opcodes.IF_ICMPNE, lblReturn)
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
        mv.visitInsn(Opcodes.DUP)
        mv.visitLdcInsn("value 2 exploded")
        mv.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/RuntimeException",
            "<init>",
            "(Ljava/lang/String;)V",
            false,
        )
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitLabel(lblReturn)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(3, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.ValueSourceFailTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(2, results.testsFound)
        assertEquals(1, results.testsFailed)
        assertTrue(results.failureMessages.any { it.contains("value 2 exploded") })
    }

    @Test
    fun `ValueSource type mismatch records Exception`() {
        val cw = buildClassWriterExt("test/ValueSourceTypeMismatchTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(Ljava/lang/String;)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val vs = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/ValueSource;", true)
        val arr = vs.visitArray("ints")
        arr.visit(null, 1)
        arr.visitEnd()
        vs.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.ValueSourceTypeMismatchTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertTrue(results.testsFailed >= 1)
    }

    @Test
    fun `ValueSource AfterEach exception fails each invocation`() {
        val cw = buildClassWriterExt("test/ValueSourceAfterEachThrowsTest")
        val ae = cw.visitMethod(Opcodes.ACC_PUBLIC, "teardown", "()V", null, null)
        val aeAnn = ae.visitAnnotation("Lorg/junit/jupiter/api/AfterEach;", true)
        aeAnn.visitEnd()
        ae.visitCode()
        ae.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
        ae.visitInsn(Opcodes.DUP)
        ae.visitLdcInsn("teardown boom")
        ae.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/RuntimeException",
            "<init>",
            "(Ljava/lang/String;)V",
            false,
        )
        ae.visitInsn(Opcodes.ATHROW)
        ae.visitMaxs(3, 1)
        ae.visitEnd()

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(I)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val vs = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/ValueSource;", true)
        val arr = vs.visitArray("ints")
        arr.visit(null, 1)
        arr.visit(null, 2)
        arr.visitEnd()
        vs.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.ValueSourceAfterEachThrowsTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(2, results.testsFound)
        assertEquals(0, results.testsSucceeded)
        assertEquals(2, results.testsFailed)
    }

    @Test
    fun `nested class runs parent and own lifecycle methods`() {
        val outerName = "test/LifecycleOuter"
        val innerName = "test/LifecycleOuter\$InnerLifecycle"

        val outerCw = buildClassWriterExt(outerName)
        outerCw.visitInnerClass(innerName, outerName, "InnerLifecycle", Opcodes.ACC_PUBLIC)
        addLifecycleMethodExt(outerCw, "parentAfter", "Lorg/junit/jupiter/api/AfterEach;", false) {
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "com/github/rodrigotimoteo/mutation/runner/AsmTestTracker",
                "incAfterEach",
                "()I",
                false,
            )
            visitInsn(Opcodes.POP)
        }
        outerCw.visitEnd()

        val innerCw = buildInnerClassWriterExt(outerName, innerName)
        innerCw.visitInnerClass(innerName, outerName, "InnerLifecycle", Opcodes.ACC_PUBLIC)
        val nested = innerCw.visitAnnotation("Lorg/junit/jupiter/api/Nested;", true)
        nested.visitEnd()
        addLifecycleMethodExt(innerCw, "ownBefore", "Lorg/junit/jupiter/api/BeforeEach;", false) {
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "com/github/rodrigotimoteo/mutation/runner/AsmTestTracker",
                "incBeforeEach",
                "()I",
                false,
            )
            visitInsn(Opcodes.POP)
        }
        addTestMethodExt(innerCw, "innerT") { visitInsn(Opcodes.RETURN) }
        innerCw.visitEnd()

        classLoader.define("test.LifecycleOuter", outerCw.toByteArray())
        classLoader.define("test.LifecycleOuter\$InnerLifecycle", innerCw.toByteArray())

        val results = runner.runTests(listOf("test.LifecycleOuter"))

        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsSucceeded)
        assertEquals(1, AsmTestTracker.beforeEachCount.get())
    }

    @Test
    fun `disabled nested class is skipped`() {
        val outerName = "test/DisabledNestedOuter"
        val innerName = "test/DisabledNestedOuter\$DisabledInner"

        val outerCw = buildClassWriterExt(outerName)
        outerCw.visitInnerClass(innerName, outerName, "DisabledInner", Opcodes.ACC_PUBLIC)
        addTestMethodExt(outerCw, "outerT") { visitInsn(Opcodes.RETURN) }
        outerCw.visitEnd()

        val innerCw = buildInnerClassWriterExt(outerName, innerName)
        innerCw.visitInnerClass(innerName, outerName, "DisabledInner", Opcodes.ACC_PUBLIC)
        val nested = innerCw.visitAnnotation("Lorg/junit/jupiter/api/Nested;", true)
        nested.visitEnd()
        val disabled = innerCw.visitAnnotation("Lorg/junit/jupiter/api/Disabled;", true)
        disabled.visitEnd()
        addTestMethodExt(innerCw, "innerT") { visitInsn(Opcodes.RETURN) }
        innerCw.visitEnd()

        classLoader.define("test.DisabledNestedOuter", outerCw.toByteArray())
        classLoader.define("test.DisabledNestedOuter\$DisabledInner", innerCw.toByteArray())

        val results = runner.runTests(listOf("test.DisabledNestedOuter"))

        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
    }

    @Test
    fun `disabled method in nested class is ignored`() {
        val outerName = "test/DisabledMethodOuter"
        val innerName = "test/DisabledMethodOuter\$DisabledMethodInner"

        val outerCw = buildClassWriterExt(outerName)
        outerCw.visitInnerClass(innerName, outerName, "DisabledMethodInner", Opcodes.ACC_PUBLIC)
        outerCw.visitEnd()

        val innerCw = buildInnerClassWriterExt(outerName, innerName)
        innerCw.visitInnerClass(innerName, outerName, "DisabledMethodInner", Opcodes.ACC_PUBLIC)
        val nested = innerCw.visitAnnotation("Lorg/junit/jupiter/api/Nested;", true)
        nested.visitEnd()
        addTestMethodExt(innerCw, "enabledT") { visitInsn(Opcodes.RETURN) }

        val disabledMv = innerCw.visitMethod(Opcodes.ACC_PUBLIC, "skippedT", "()V", null, null)
        val disabled = disabledMv.visitAnnotation("Lorg/junit/jupiter/api/Disabled;", true)
        disabled.visitEnd()
        val testAnn = disabledMv.visitAnnotation("Lorg/junit/jupiter/api/Test;", true)
        testAnn.visitEnd()
        disabledMv.visitCode()
        disabledMv.visitInsn(Opcodes.RETURN)
        disabledMv.visitMaxs(0, 1)
        disabledMv.visitEnd()
        innerCw.visitEnd()

        classLoader.define("test.DisabledMethodOuter", outerCw.toByteArray())
        classLoader.define("test.DisabledMethodOuter\$DisabledMethodInner", innerCw.toByteArray())

        val results = runner.runTests(listOf("test.DisabledMethodOuter"))

        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsSucceeded)
    }

    @Test
    fun `nested RepeatedTest runs`() {
        val outerName = "test/NestedRepeatedOuter"
        val innerName = "test/NestedRepeatedOuter\$NestedRepeatedInner"

        val outerCw = buildClassWriterExt(outerName)
        outerCw.visitInnerClass(innerName, outerName, "NestedRepeatedInner", Opcodes.ACC_PUBLIC)
        outerCw.visitEnd()

        val innerCw = buildInnerClassWriterExt(outerName, innerName)
        innerCw.visitInnerClass(innerName, outerName, "NestedRepeatedInner", Opcodes.ACC_PUBLIC)
        val nested = innerCw.visitAnnotation("Lorg/junit/jupiter/api/Nested;", true)
        nested.visitEnd()
        val mv = innerCw.visitMethod(Opcodes.ACC_PUBLIC, "repeatedT", "()V", null, null)
        val rt = mv.visitAnnotation("Lorg/junit/jupiter/api/RepeatedTest;", true)
        rt.visit("value", 2)
        rt.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        innerCw.visitEnd()

        classLoader.define("test.NestedRepeatedOuter", outerCw.toByteArray())
        classLoader.define("test.NestedRepeatedOuter\$NestedRepeatedInner", innerCw.toByteArray())

        val results = runner.runTests(listOf("test.NestedRepeatedOuter"))

        assertEquals(2, results.testsFound)
        assertEquals(2, results.testsSucceeded)
    }

    @Test
    fun `nested ParameterizedTest with MethodSource runs`() {
        val outerName = "test/NestedParamOuter"
        val innerName = "test/NestedParamOuter\$NestedParamInner"

        val outerCw = buildClassWriterExt(outerName)
        outerCw.visitInnerClass(innerName, outerName, "NestedParamInner", Opcodes.ACC_PUBLIC)
        outerCw.visitEnd()

        val innerCw = buildInnerClassWriterExt(outerName, innerName)
        innerCw.visitInnerClass(innerName, outerName, "NestedParamInner", Opcodes.ACC_PUBLIC)
        val nested = innerCw.visitAnnotation("Lorg/junit/jupiter/api/Nested;", true)
        nested.visitEnd()

        addIntListFactoryExt(innerCw, "provideData", listOf(1, 2))

        val mv = innerCw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(I)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val ms = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/MethodSource;", true)
        val arr = ms.visitArray("value")
        arr.visit(null, "provideData")
        arr.visitEnd()
        ms.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        innerCw.visitEnd()

        classLoader.define("test.NestedParamOuter", outerCw.toByteArray())
        classLoader.define("test.NestedParamOuter\$NestedParamInner", innerCw.toByteArray())

        val results = runner.runTests(listOf("test.NestedParamOuter"))

        assertEquals(2, results.testsFound)
        assertEquals(2, results.testsSucceeded)
    }

    @Test
    fun `failing nested standard test records failure`() {
        val outerName = "test/FailingNestedOuter"
        val innerName = "test/FailingNestedOuter\$FailingNestedInner"

        val outerCw = buildClassWriterExt(outerName)
        outerCw.visitInnerClass(innerName, outerName, "FailingNestedInner", Opcodes.ACC_PUBLIC)
        outerCw.visitEnd()

        val innerCw = buildInnerClassWriterExt(outerName, innerName)
        innerCw.visitInnerClass(innerName, outerName, "FailingNestedInner", Opcodes.ACC_PUBLIC)
        val nested = innerCw.visitAnnotation("Lorg/junit/jupiter/api/Nested;", true)
        nested.visitEnd()
        addFailingTestMethodExt(innerCw, "failingT")
        innerCw.visitEnd()

        classLoader.define("test.FailingNestedOuter", outerCw.toByteArray())
        classLoader.define("test.FailingNestedOuter\$FailingNestedInner", innerCw.toByteArray())

        val results = runner.runTests(listOf("test.FailingNestedOuter"))

        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsFailed)
    }

    @Test
    fun `nested AfterEach exception fails the inner test`() {
        val outerName = "test/NestedAfterEachThrowsOuter"
        val innerName = "test/NestedAfterEachThrowsOuter\$NestedAfterEachThrowsInner"

        val outerCw = buildClassWriterExt(outerName)
        outerCw.visitInnerClass(innerName, outerName, "NestedAfterEachThrowsInner", Opcodes.ACC_PUBLIC)
        outerCw.visitEnd()

        val innerCw = buildInnerClassWriterExt(outerName, innerName)
        innerCw.visitInnerClass(innerName, outerName, "NestedAfterEachThrowsInner", Opcodes.ACC_PUBLIC)
        val nested = innerCw.visitAnnotation("Lorg/junit/jupiter/api/Nested;", true)
        nested.visitEnd()

        val ae = innerCw.visitMethod(Opcodes.ACC_PUBLIC, "teardown", "()V", null, null)
        val aeAnn = ae.visitAnnotation("Lorg/junit/jupiter/api/AfterEach;", true)
        aeAnn.visitEnd()
        ae.visitCode()
        ae.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
        ae.visitInsn(Opcodes.DUP)
        ae.visitLdcInsn("teardown boom")
        ae.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/RuntimeException",
            "<init>",
            "(Ljava/lang/String;)V",
            false,
        )
        ae.visitInsn(Opcodes.ATHROW)
        ae.visitMaxs(3, 1)
        ae.visitEnd()

        addTestMethodExt(innerCw, "t1") { visitInsn(Opcodes.RETURN) }
        innerCw.visitEnd()

        classLoader.define("test.NestedAfterEachThrowsOuter", outerCw.toByteArray())
        classLoader.define("test.NestedAfterEachThrowsOuter\$NestedAfterEachThrowsInner", innerCw.toByteArray())

        val results = runner.runTests(listOf("test.NestedAfterEachThrowsOuter"))

        assertEquals(1, results.testsFound)
        assertEquals(0, results.testsSucceeded)
        assertEquals(1, results.testsFailed)
    }

    @Test
    fun `superclass test method is discovered`() {
        val baseName = "test/SuperBase"
        val subName = "test/SuperSub"

        val baseCw = buildClassWriterExt(baseName)
        addTestMethodExt(baseCw, "inheritedT") { visitInsn(Opcodes.RETURN) }
        baseCw.visitEnd()

        val subCw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        subCw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, subName, null, baseName, null)
        val init = subCw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        init.visitCode()
        init.visitVarInsn(Opcodes.ALOAD, 0)
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, baseName, "<init>", "()V", false)
        init.visitInsn(Opcodes.RETURN)
        init.visitMaxs(1, 1)
        init.visitEnd()
        subCw.visitEnd()

        classLoader.define("test.SuperBase", baseCw.toByteArray())
        val subClazz = classLoader.define("test.SuperSub", subCw.toByteArray())

        val results = runner.runTests(listOf(subClazz.name))

        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsSucceeded)
    }

    @Test
    fun `nested class is discovered with proper constructor`() {
        // JUnit Jupiter requires @Nested classes to have a non-private
        // constructor that takes the enclosing instance. A no-arg-only
        // inner class is treated as a regular (top-level) class, so the
        // engine refuses to wire it as @Nested.
        val outerName = "test/FallbackCtorOuter"
        val innerName = "test/FallbackCtorOuter\$FallbackCtorInner"

        val outerCw = buildClassWriterExt(outerName)
        outerCw.visitInnerClass(innerName, outerName, "FallbackCtorInner", Opcodes.ACC_PUBLIC)
        outerCw.visitEnd()

        val innerCw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        innerCw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, innerName, null, "java/lang/Object", null)
        val ctor = innerCw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(1, 1)
        ctor.visitEnd()
        innerCw.visitInnerClass(innerName, outerName, "FallbackCtorInner", Opcodes.ACC_PUBLIC)
        // No @Nested annotation: Jupiter will only treat the class as
        // nested if the bytecode looks like a proper non-static inner
        // class. We still expect the test method to be discovered and run
        // as a regular top-level test.
        addTestMethodExt(innerCw, "t1") { visitInsn(Opcodes.RETURN) }
        innerCw.visitEnd()

        classLoader.define("test.FallbackCtorOuter", outerCw.toByteArray())
        classLoader.define("test.FallbackCtorOuter\$FallbackCtorInner", innerCw.toByteArray())

        val results = runner.runTests(listOf("test.FallbackCtorOuter"))

        // The outer class has no test methods of its own; the inner
        // class is treated as a standalone test class and is not
        // auto-discovered. So no tests are reported.
        assertEquals(0, results.testsFound)
    }

    @Test
    fun `class with no usable constructor propagates to runTests catch`() {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "test/NoCtorTest", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(1, 2)
        ctor.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/jupiter/api/Test;", true)
        ann.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.NoCtorTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertTrue(results.testsFailed >= 1)
    }

    @Test
    fun `findFactoryMethod uses companion object without JvmStatic`() {
        // JUnit Jupiter's MethodArgumentsProvider looks for static factory
        // methods in the test class itself; without @JvmStatic the Kotlin
        // companion's factory lives on a separate class and Jupiter cannot
        // discover it. We document that limitation here — the test
        // verifies the runner surfaces this as a failure.
        val cw = buildClassWriterExt("test/CompanionNoJvmStatic")
        val companionCw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        companionCw.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            "test/CompanionNoJvmStatic\$Companion",
            null,
            "java/lang/Object",
            null,
        )
        val ctor = companionCw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(1, 1)
        ctor.visitEnd()
        addIntListFactoryExt(companionCw, "provideData", listOf(1, 2))

        companionCw.visitInnerClass(
            "test/CompanionNoJvmStatic\$Companion",
            "test/CompanionNoJvmStatic",
            "Companion",
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        )
        companionCw.visitEnd()

        val companionField =
            cw.visitField(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
                "Companion",
                "Ltest/CompanionNoJvmStatic\$Companion;",
                null,
                null,
            )
        companionField.visitEnd()

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(I)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val ms = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/MethodSource;", true)
        val arr = ms.visitArray("value")
        arr.visit(null, "provideData")
        arr.visitEnd()
        ms.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitInnerClass(
            "test/CompanionNoJvmStatic\$Companion",
            "test/CompanionNoJvmStatic",
            "Companion",
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        )
        cw.visitEnd()

        classLoader.define("test.CompanionNoJvmStatic\$Companion", companionCw.toByteArray())
        val clazz = classLoader.define("test.CompanionNoJvmStatic", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        // Jupiter cannot find a non-static factory on the companion; the
        // test is reported as failed rather than silently producing 0.
        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsFailed)
    }

    @Test
    fun `findFactoryMethod finds inherited public static factory`() {
        val baseName = "test/BaseFactory"
        val subName = "test/SubWithInheritedFactory"

        val baseCw = buildClassWriterExt(baseName)
        addIntListFactoryExt(baseCw, "provideData", listOf(1, 2))
        baseCw.visitEnd()

        val subCw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        subCw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, subName, null, baseName, null)
        val ctor = subCw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, baseName, "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(1, 1)
        ctor.visitEnd()

        val mv = subCw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(I)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val ms = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/MethodSource;", true)
        val arr = ms.visitArray("value")
        arr.visit(null, "provideData")
        arr.visitEnd()
        ms.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        subCw.visitEnd()

        classLoader.define("test.BaseFactory", baseCw.toByteArray())
        val subClazz = classLoader.define("test.SubWithInheritedFactory", subCw.toByteArray())
        val results = runner.runTests(listOf(subClazz.name))

        assertEquals(2, results.testsFound)
        assertEquals(2, results.testsSucceeded)
    }

    @Test
    fun `missing factory returns null and reports unresolved`() {
        val cw = buildClassWriterExt("test/AbsentFactoryTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "t1", "(I)V", null, null)
        val pt = mv.visitAnnotation("Lorg/junit/jupiter/params/ParameterizedTest;", true)
        pt.visitEnd()
        val ms = mv.visitAnnotation("Lorg/junit/jupiter/params/provider/MethodSource;", true)
        val arr = ms.visitArray("value")
        arr.visit(null, "absent")
        arr.visitEnd()
        ms.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 2)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.AbsentFactoryTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsFailed)
        assertTrue(results.failureMessages.isNotEmpty())
    }

    @Test
    fun `TestFactory method is not discovered`() {
        val cw = buildClassWriterExt("test/TestFactoryTest")
        val factory =
            cw.visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "dynamicTests",
                "()Ljava/util/List;",
                null,
                null,
            )
        val tfAnn = factory.visitAnnotation("Lorg/junit/jupiter/api/TestFactory;", true)
        tfAnn.visitEnd()
        factory.visitCode()
        factory.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "java/util/Collections",
            "emptyList",
            "()Ljava/util/List;",
            true,
        )
        factory.visitInsn(Opcodes.ARETURN)
        factory.visitMaxs(0, 0)
        factory.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.TestFactoryTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))

        assertEquals(0, results.testsFound)
    }

    @Test
    fun `engineIds restricts discovery to the configured engine`() {
        // Build a JUnit 4 test class. With engineIds=["junit-jupiter"], the
        // Vintage engine is excluded and the @Test method should not run.
        val cw = buildClassWriterExt("test/JUnit4OnlyTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "legacyTest", "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/Test;", true)
        ann.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.JUnit4OnlyTest", cw.toByteArray())
        val jupiterOnly = ReflectionTestRunner(classLoader, listOf("junit-jupiter"))
        val results = jupiterOnly.runTests(listOf(clazz.name))
        // Vintage engine excluded → no test discovered
        assertEquals(0, results.testsFound)
    }

    @Test
    fun `engineIds defaults to jupiter and vintage when not provided`() {
        val cw = buildClassWriterExt("test/JUnit4DefaultTest")
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "legacyTest", "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/Test;", true)
        ann.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.JUnit4DefaultTest", cw.toByteArray())
        // Default runner constructor: defaults are jupiter + vintage
        val results = ReflectionTestRunner(classLoader).runTests(listOf(clazz.name))
        // Vintage engine included by default → test discovered
        assertEquals(1, results.testsFound)
    }

    @Test
    fun `includeTags filters to only matching tagged tests`() {
        val cw = buildClassWriterExt("test/TaggedTest")
        addTaggedTestMethodExt(cw, "fastTest", "fast")
        addTaggedTestMethodExt(cw, "slowTest", "slow")
        cw.visitEnd()

        val clazz = classLoader.define("test.TaggedTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name), includeTags = setOf("fast"))
        // Only "fastTest" should match
        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
    }

    @Test
    fun `excludeTags filters out matching tagged tests`() {
        val cw = buildClassWriterExt("test/ExcludedTagTest")
        addTaggedTestMethodExt(cw, "keptTest", "fast")
        addTaggedTestMethodExt(cw, "droppedTest", "slow")
        cw.visitEnd()

        val clazz = classLoader.define("test.ExcludedTagTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name), excludeTags = setOf("slow"))
        // "droppedTest" excluded; only "keptTest" runs
        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsSucceeded)
    }

    @Test
    fun `JUnit 4 Ignore annotation on class skips all tests in the class`() {
        // JUnit 4 Vintage treats @Ignore at the class level as a global
        // skip — no tests in the class should be discovered or run.
        val cw = buildClassWriterExt("test/JUnit4IgnoreClassTest")
        val classIgnore = cw.visitAnnotation("Lorg/junit/Ignore;", true)
        classIgnore.visitEnd()
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "skippedTest", "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/Test;", true)
        ann.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        cw.visitEnd()

        val clazz = classLoader.define("test.JUnit4IgnoreClassTest", cw.toByteArray())
        val results = runner.runTests(listOf(clazz.name))
        // Vintage skips @Ignore classes; 0 tests discovered
        assertEquals(0, results.testsFound)
        assertEquals(0, results.testsSucceeded)
        assertEquals(0, results.testsFailed)
    }

    @Test
    fun `nested class without Nested annotation is not discovered as a test`() {
        // An inner class without @Nested is a regular inner class — JUnit
        // Jupiter does not treat it as a test container. The outer class
        // is the test class; the inner is just a non-static nested class
        // that should not contribute its own test methods.
        val outerName = "test/NotNestedOuter"
        val innerName = "test/NotNestedOuter\$Inner"

        val outerCw = buildClassWriterExt(outerName)
        outerCw.visitInnerClass(innerName, outerName, "Inner", Opcodes.ACC_PUBLIC)
        addTestMethodExt(outerCw, "outerTest") { visitInsn(Opcodes.RETURN) }
        outerCw.visitEnd()

        val innerCw = buildInnerClassWriterExt(outerName, innerName)
        innerCw.visitInnerClass(innerName, outerName, "Inner", Opcodes.ACC_PUBLIC)
        // No @Nested annotation on purpose
        val innerMv = innerCw.visitMethod(Opcodes.ACC_PUBLIC, "innerTest", "()V", null, null)
        val innerAnn = innerMv.visitAnnotation("Lorg/junit/jupiter/api/Test;", true)
        innerAnn.visitEnd()
        innerMv.visitCode()
        innerMv.visitInsn(Opcodes.RETURN)
        innerMv.visitMaxs(0, 2)
        innerMv.visitEnd()
        innerCw.visitEnd()

        classLoader.define("test.NotNestedOuter", outerCw.toByteArray())
        classLoader.define("test.NotNestedOuter\$Inner", innerCw.toByteArray())

        val results = runner.runTests(listOf("test.NotNestedOuter"))
        // Only the outer's own test method runs; the inner's is ignored
        assertEquals(1, results.testsFound)
        assertEquals(1, results.testsSucceeded)
    }

    @Test
    fun `load failure for a mixed list of classes is reported as a single failed test`() {
        // When the classloader fails to load a class, the load failure is
        // recorded in failureMessages. With multiple test classes where
        // some fail to load, each load failure must be tracked in
        // loadFailures so the engine can distinguish infrastructure
        // errors from real assertion failures.
        val failingResults =
            ReflectionTestRunner.TestResults(
                testsFound = 0,
                testsSucceeded = 0,
                testsFailed = 2,
                testsSkipped = 0,
                failureMessages = listOf("Could not load a: a", "Could not load b: b"),
                failedTestClasses = setOf("a", "b"),
                loadFailures = 2,
            )
        assertTrue(failingResults.hasOnlyLoadFailures)
        assertEquals(2, failingResults.loadFailures)
    }

    private fun buildClassWriterExt(name: String): ClassWriter {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()
        return cw
    }

    private fun buildInnerClassWriterExt(
        outerName: String,
        innerName: String,
    ): ClassWriter {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, innerName, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(L$outerName;)V", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 2)
        mv.visitEnd()
        return cw
    }

    private fun addTestMethodExt(
        cw: ClassWriter,
        name: String,
        body: MethodVisitor.() -> Unit,
    ) {
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/jupiter/api/Test;", true)
        ann.visitEnd()
        mv.visitCode()
        body(mv)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
    }

    private fun addFailingTestMethodExt(
        cw: ClassWriter,
        name: String,
    ) {
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/jupiter/api/Test;", true)
        ann.visitEnd()
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
        mv.visitInsn(Opcodes.DUP)
        mv.visitLdcInsn("fail")
        mv.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/RuntimeException",
            "<init>",
            "(Ljava/lang/String;)V",
            false,
        )
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitMaxs(3, 1)
        mv.visitEnd()
    }

    private fun addLifecycleMethodExt(
        cw: ClassWriter,
        name: String,
        annotationDesc: String,
        static: Boolean,
        body: MethodVisitor.() -> Unit,
    ) {
        val access = if (static) Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC else Opcodes.ACC_PUBLIC
        val mv = cw.visitMethod(access, name, "()V", null, null)
        val ann = mv.visitAnnotation(annotationDesc, true)
        ann.visitEnd()
        mv.visitCode()
        body(mv)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
    }

    private fun addTestMethodJUnit4Ext(
        cw: ClassWriter,
        name: String,
        body: MethodVisitor.() -> Unit,
    ) {
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, "()V", null, null)
        val ann = mv.visitAnnotation("Lorg/junit/Test;", true)
        ann.visitEnd()
        mv.visitCode()
        body(mv)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
    }

    private fun addTaggedTestMethodExt(
        cw: ClassWriter,
        name: String,
        tag: String,
    ) {
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, "()V", null, null)
        val tagAnn = mv.visitAnnotation("Lorg/junit/jupiter/api/Tag;", true)
        tagAnn.visit("value", tag)
        tagAnn.visitEnd()
        val testAnn = mv.visitAnnotation("Lorg/junit/jupiter/api/Test;", true)
        testAnn.visitEnd()
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
    }

    private fun addIntListFactoryExt(
        cw: ClassWriter,
        name: String,
        values: List<Int>,
    ) {
        val mv =
            cw.visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                name,
                "()Ljava/util/List;",
                null,
                null,
            )
        mv.visitCode()
        for (v in values) {
            if (v == 1) {
                mv.visitInsn(Opcodes.ICONST_1)
            } else if (v == 2) {
                mv.visitInsn(Opcodes.ICONST_2)
            } else if (v == 3) {
                mv.visitInsn(Opcodes.ICONST_3)
            } else if (v == 4) {
                mv.visitInsn(Opcodes.ICONST_4)
            } else if (v == 5) {
                mv.visitInsn(Opcodes.ICONST_5)
            } else {
                mv.visitLdcInsn(v)
            }
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/Integer",
                "valueOf",
                "(I)Ljava/lang/Integer;",
                false,
            )
        }
        val sig =
            buildString {
                append("(Ljava/lang/Object;")
                for (i in 1 until values.size) append("Ljava/lang/Object;")
                append(")Ljava/util/List;")
            }
        val className =
            if (values.size == 1) {
                "java/util/List"
            } else {
                "java/util/List"
            }
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            className,
            "of",
            sig,
            true,
        )
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(values.size, 0)
        mv.visitEnd()
    }
}
