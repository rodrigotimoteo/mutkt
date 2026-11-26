package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

// Robolectric caches the project classes it loaded in JVM-wide statics, so a reused child would test a stale class.
@Timeout(value = 120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ForkedSandboxCacheTest {
    @TempDir
    lateinit var dir: Path

    private var executor: ForkedTestExecutor? = null

    @AfterEach
    fun closeExecutor() {
        executor?.close()
    }

    // Baseline, a killing mutant, then the original again: outcomes after the baseline, and children started.
    private fun flow(
        test: String,
        extraClasspath: List<Path> = emptyList(),
    ): Pair<List<Outcome>, Int> {
        val settings = forkSettingsFor(dir, test).let { it.copy(classpath = it.classpath + extraClasspath) }
        val pool = ForkedTestExecutor(settings).also { executor = it }
        assertEquals(Outcome.Survived, pool.baseline(LONG).outcome)
        val outcomes = listOf(mutantBytes(), mutantBytes(CALC)).map { pool.run(mutantOf(), it, LONG) }
        return outcomes to pool.startedChildren
    }

    @Test
    fun `a Robolectric jar on the classpath gives every mutant a fresh child`() {
        val jar = writeJar(dir.resolve("robolectric.jar"), sandboxManager)
        assertEquals(listOf(KILLED, Outcome.Survived) to 3, flow(SANDBOX_TEST, listOf(jar)))
    }

    @Test
    fun `a Robolectric class directory on the classpath gives every mutant a fresh child`() {
        val classes = writeClasses(dir.resolve("robolectric"), sandboxManager.map { ClassFile(it.key, it.value) })
        assertEquals(listOf(KILLED, Outcome.Survived) to 3, flow(SANDBOX_TEST, listOf(classes)))
    }

    @Test
    fun `without Robolectric one child serves every mutant`() {
        val test = "class CalcTest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } }"
        assertEquals(listOf(KILLED, Outcome.Survived) to 1, flow(test))
    }

    private companion object {
        val LONG = 60.seconds
        val KILLED =
            Outcome.Killed("CalcTest.adds", "org.opentest4j.AssertionFailedError: expected: <5> but was: <-1>")

        // Like Robolectric's sandbox: the first call keeps the class under test, read through the context loader.
        val sandboxManager =
            compileKotlin(
                """
                package org.robolectric.internal

                class SandboxManager {
                    companion object {
                        private var calc: Class<*>? = null

                        @JvmStatic
                        fun add(a: Int, b: Int): Int {
                            val type = calc ?: Thread.currentThread().contextClassLoader.loadClass("Calc").also { calc = it }
                            val add = type.getMethod("add", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                            return add.invoke(type.getDeclaredConstructor().newInstance(), a, b) as Int
                        }
                    }
                }
                """.trimIndent(),
            )

        // Reflection: the fake sandbox is on the child's classpath only, not on the fixture's compile classpath.
        const val SANDBOX_TEST =
            "class CalcTest { @Test fun adds() { " +
                "val add = Class.forName(\"org.robolectric.internal.SandboxManager\")" +
                ".getMethod(\"add\", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType); " +
                "assertEquals(5, add.invoke(null, 2, 3)) } }"
    }
}
