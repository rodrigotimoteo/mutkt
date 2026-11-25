package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.testing.buildClass
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.platform.commons.JUnitException
import org.objectweb.asm.Opcodes.ACONST_NULL
import org.objectweb.asm.Opcodes.IRETURN
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class JUnitTestExecutorTest {
    private val executors = mutableListOf<JUnitTestExecutor>()

    @AfterEach
    fun closeExecutors() = executors.forEach { it.close() }

    private fun executor(
        tests: String,
        production: String = CALC,
    ) = executorFor("$IMPORTS$production\n$tests").also { executors += it }

    private fun filtered(
        engines: List<String> = listOf("junit-jupiter"),
        include: List<String> = emptyList(),
        exclude: List<String> = emptyList(),
    ): JUnitTestExecutor {
        val (project, tests) = splitClasses("$IMPORTS$CALC\n$tagged")
        return JUnitTestExecutor(project, tests, runtimeJars(), engines, include, exclude).also { executors += it }
    }

    private fun JUnitTestExecutor.runMutant(bytes: ByteArray = mutantBytes()) = run(mutantOf(), bytes, deadline)

    private val tagged =
        "class CalcTest { @Tag(\"fast\") @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } @Test fun ok() {} }"

    private val addsTest = "class CalcTest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } }"

    @Test
    fun `tests are discovered before the run reports that execution started`() {
        // Jupiter instantiates the display name generator while it discovers CalcTest.
        val tests =
            "class Named : DisplayNameGenerator.Standard() { " +
                "init { System.setProperty(\"$DISCOVERED\", \"yes\") } }\n" +
                "@DisplayNameGeneration(Named::class) $addsTest"
        var seen: String? = null
        try {
            val outcome =
                executor(tests).run(mutantOf(), mutantBytes(), deadline) { seen = System.getProperty(DISCOVERED) }
            assertEquals(Outcome.Killed("CalcTest.adds"), outcome.outcome)
            assertEquals("yes", seen)
        } finally {
            System.clearProperty(DISCOVERED)
        }
    }

    @Test
    fun `failing assertion kills the mutant and names the test`() {
        assertEquals(Outcome.Killed("CalcTest.adds"), executor(addsTest).runMutant())
    }

    @Test
    fun `passing tests let the mutant survive`() {
        assertEquals(Outcome.Survived, executor(addsTest).runMutant(mutantBytes(CALC)))
    }

    @Test
    fun `disabled tests are skipped and do not kill`() {
        val tests =
            "class CalcTest { @Disabled @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } @Test fun ok() {} }"
        assertEquals(Outcome.Survived, executor(tests).runMutant())
    }

    @Test
    fun `only disabled tests means no tests`() {
        val tests = "class CalcTest { @Disabled @Test fun adds() { fail<Unit>() } }"
        assertEquals(Outcome.NoTests, executor(tests).runMutant())
    }

    @Test
    fun `aborted assumptions are not kills`() {
        val tests =
            "class CalcTest { @Test fun adds() { Assumptions.assumeTrue(false); fail<Unit>() } }"
        assertEquals(Outcome.Survived, executor(tests).runMutant())
    }

    @Test
    fun `beforeAll failure kills by class name`() {
        val tests =
            "class CalcTest { companion object { " +
                "@BeforeAll @JvmStatic fun setUp() { check(Calc().add(1, 1) == 2) } } @Test fun adds() {} }"
        assertEquals(Outcome.Killed("CalcTest"), executor(tests).runMutant())
    }

    @Test
    fun `class without tests means no tests`() {
        assertEquals(Outcome.NoTests, executor("class CalcTest { fun adds() {} }").runMutant())
    }

    @Test
    fun `invalid bytecode is a load error`() {
        val invalid = buildClass("Calc", "add") { visitInsn(ACONST_NULL).also { visitInsn(IRETURN) } }
        val outcome = executor(addsTest).runMutant(invalid)
        assertTrue(outcome is Outcome.LoadError && "VerifyError" in outcome.message, outcome.toString())
    }

    @Test
    fun `truncated bytes are a load error`() {
        val outcome = executor(addsTest).runMutant(mutantBytes().copyOf(10))
        assertTrue(outcome is Outcome.LoadError && "ClassFormatError" in outcome.message, outcome.toString())
    }

    @Test
    fun `static state is not shared between runs`() {
        val tests = "class CalcTest { @Test fun counts() { Counter.n++; assertEquals(1, Counter.n) } }"
        val executor = executor(tests, "$CALC\nobject Counter { var n = 0 }")
        assertEquals(Outcome.Survived, executor.runMutant(mutantBytes(CALC)))
        assertEquals(Outcome.Survived, executor.runMutant(mutantBytes(CALC)))
    }

    @Test
    fun `one executor serves different mutants in turn`() {
        val executor = executor(addsTest)
        assertEquals(Outcome.Killed("CalcTest.adds"), executor.runMutant())
        assertEquals(Outcome.Survived, executor.runMutant(mutantBytes(CALC)))
        assertEquals(Outcome.Killed("CalcTest.adds"), executor.runMutant())
    }

    @Test
    fun `context class loader is restored`() {
        val before = Thread.currentThread().contextClassLoader
        val executor = executor(addsTest)
        executor.runMutant()
        assertSame(before, Thread.currentThread().contextClassLoader)
        executor.runMutant(mutantBytes().copyOf(10))
        assertSame(before, Thread.currentThread().contextClassLoader)
    }

    @Test
    fun `classes after the first failing class are not run`() {
        val tests =
            "class AFirstTest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } }\n" +
                "class BSecondTest { @Test fun marks() { System.setProperty(\"$MARK\", \"ran\") } }"
        val executor = executor(tests)
        try {
            assertEquals(Outcome.Killed("AFirstTest.adds"), executor.runMutant())
            assertNull(System.getProperty(MARK))
            assertEquals(Outcome.Survived, executor.runMutant(mutantBytes(CALC)))
            assertEquals("ran", System.getProperty(MARK))
        } finally {
            System.clearProperty(MARK)
        }
    }

    @Test
    fun `nested classes run once through their enclosing class`() {
        val tests =
            "class CalcTest { @Test fun top() { $TICK } " +
                "@Nested inner class Deep { @Test fun deep() { $TICK } } }\n" +
                "class Holder { class Inner { @Test fun inner() { $TICK } } }"
        try {
            assertEquals(Outcome.Survived, executor(tests).runMutant(mutantBytes(CALC)))
            assertEquals("3", System.getProperty(COUNT))
        } finally {
            System.clearProperty(COUNT)
        }
    }

    @Test
    fun `failing nested test kills by the nested class`() {
        val tests = "class CalcTest { @Nested inner class Deep { @Test fun deep() { fail<Unit>() } } }"
        assertEquals(Outcome.Killed("CalcTest\$Deep.deep"), executor(tests).runMutant())
    }

    @Test
    fun `test class that cannot link is a load error`() {
        val classes =
            compileKotlin("$IMPORTS$CALC\nopen class Base\nclass CalcTest : Base() { @Test fun adds() {} }", true)
        val files = (classes - "Base").map { ClassFile(it.key, it.value) }
        JUnitTestExecutor(
            files.filter { it.name == "Calc" },
            files.filter { it.name == "CalcTest" },
            runtimeJars(),
        ).use {
            val outcome = it.runMutant()
            assertTrue(outcome is Outcome.LoadError && "Base" in outcome.message, outcome.toString())
        }
    }

    @Test
    fun `packaged classes with dotted names work`() {
        val source =
            "package a.b\n$IMPORTS$CALC\n" +
                "class CalcTest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } }"
        val executor = executorFor(source, setOf("a.b.Calc")).also { executors += it }
        val mutant = mutantOf("a.b.Calc")
        val mutated = mutantBytes("package a.b\n$CALC_MUTANT", "a.b.Calc")
        assertEquals(Outcome.Killed("a.b.CalcTest.adds"), executor.run(mutant, mutated, deadline))
        assertEquals(Outcome.Survived, executor.run(mutant, mutantBytes("package a.b\n$CALC", "a.b.Calc"), deadline))
    }

    @Test
    fun `concurrent runs on one executor keep their own outcomes`() {
        val executor = executor(addsTest)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val killed = pool.submit<Outcome> { start.await().let { executor.runMutant() } }
            val survived = pool.submit<Outcome> { start.await().let { executor.runMutant(mutantBytes(CALC)) } }
            start.countDown()
            assertEquals(Outcome.Killed("CalcTest.adds"), killed.get(60, TimeUnit.SECONDS))
            assertEquals(Outcome.Survived, survived.get(60, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `context class loader is restored when an error propagates`() {
        val before = Thread.currentThread().contextClassLoader
        val executor = executor("class CalcTest { @Test fun adds() { throw OutOfMemoryError(\"boom\") } }")
        assertThrows<OutOfMemoryError> { executor.runMutant() }
        assertSame(before, Thread.currentThread().contextClassLoader)
    }

    @Test
    fun `run after close fails clearly`() {
        val executor = executor(addsTest)
        executor.close()
        val error = assertThrows<IllegalStateException> { executor.runMutant() }
        assertTrue("closed" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `excluded tags skip the killing test, so the mutant survives`() {
        assertEquals(Outcome.Killed("CalcTest.adds"), filtered().runMutant())
        assertEquals(Outcome.Survived, filtered(exclude = listOf("fast")).runMutant())
    }

    @Test
    fun `included tags run only the matching tests`() {
        assertEquals(Outcome.Killed("CalcTest.adds"), filtered(include = listOf("fast")).runMutant())
        assertEquals(Outcome.NoTests, filtered(include = listOf("slow")).runMutant())
    }

    @Test
    fun `an engine missing from the classpath fails the run`() {
        val error = assertThrows<JUnitException> { filtered(engines = listOf("junit-vintage")).runMutant() }
        assertTrue("No TestEngine ID matched" in error.message.orEmpty(), error.message)
    }

    private companion object {
        const val MARK = "mutkt.test.second-class-ran"
        const val DISCOVERED = "mutkt.test.discovered"
        const val COUNT = "mutkt.test.runs"
        const val TICK =
            "System.setProperty(\"$COUNT\", (System.getProperty(\"$COUNT\", \"0\").toInt() + 1).toString())"
    }
}
