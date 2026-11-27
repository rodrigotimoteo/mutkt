package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ForkedCoverageTest {
    @TempDir
    lateinit var dir: Path

    private var executor: ForkedTestExecutor? = null

    @AfterEach
    fun closeExecutor() {
        executor?.close()
    }

    private val fixture =
        coverageFixture(
            "class ATest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } }\n" +
                "class CTest { @Test fun subtracts() { assertEquals(1, Calc().sub(3, 2)) } }",
        )

    private fun executor(extraClasspath: List<Path> = emptyList()): ForkedTestExecutor {
        val settings =
            forkSettingsFor(dir, "").copy(
                projectClassDir = writeClasses(dir.resolve("project"), fixture.project),
                testClassDir = writeClasses(dir.resolve("tests"), fixture.tests),
                classpath = runtimeJars() + extraClasspath,
            )
        return ForkedTestExecutor(settings).also { executor = it }
    }

    @Test
    fun `the forked baseline runs on the instrumented classes and returns their coverage`() {
        val baseline = executor().baseline(LONG, fixture.instrumented)
        assertEquals(Outcome.Survived, baseline.outcome)
        val hits = baseline.coverage!!.tests.map { it.test to it.hits }
        assertEquals(listOf(TestId("ATest") to setOf(ADD), TestId("CTest") to setOf(SUB)), hits)
    }

    @Test
    fun `a forked run executes only the given test classes, in order`() {
        val pool = executor()
        val run = { tests: List<String> -> pool.run(mutantOf(), bothWrong, LONG, tests.map(::TestId)) }
        assertKilledBy("CTest.subtracts", run(listOf("CTest", "ATest")))
        assertKilledBy("ATest.adds", run(listOf("ATest", "CTest")))
        assertEquals(Outcome.NoTests, run(emptyList()))
    }

    @Test
    fun `with Robolectric on the classpath the baseline has no coverage`() {
        val robolectric = mapOf("org.robolectric.internal.SandboxManager" to compileKotlin("class X").getValue("X"))
        val jar = writeJar(Files.createDirectories(dir.resolve("jars")).resolve("robolectric.jar"), robolectric)
        val baseline = executor(listOf(jar)).baseline(LONG, fixture.instrumented)
        assertEquals(Outcome.Survived, baseline.outcome)
        assertNull(baseline.coverage)
    }

    private companion object {
        val LONG = 60.seconds

        val bothWrong = mutantBytes("class Calc { fun add(a: Int, b: Int) = a - b\nfun sub(a: Int, b: Int) = a + b }")
    }
}
