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
    fun `BeforeAll failure aborts all tests`() {
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

        assertEquals(0, results.testsFound)
        assertEquals(1, results.testsFailed)
        assertTrue(results.failureMessages.any { it.contains("@BeforeAll") })
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
