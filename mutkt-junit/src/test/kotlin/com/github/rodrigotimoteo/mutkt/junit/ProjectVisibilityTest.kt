package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

// Libraries come from the dependency classpath yet must see the project's classes, as in the project's own test task.
@Timeout(value = 120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ProjectVisibilityTest {
    @TempDir
    lateinit var dir: Path

    private val closeables = mutableListOf<AutoCloseable>()

    @AfterEach
    fun close() = closeables.forEach(AutoCloseable::close)

    private class Layout(
        val project: List<ClassFile>,
        val tests: List<ClassFile>,
        val mainDir: Path,
        val testDir: Path,
        val library: Path,
    )

    // Finder goes into a jar: it stands in for minutest or Kotest, which resolve names with one-argument Class.forName.
    private fun layout(): Layout {
        val classes = compileKotlin(SOURCE, inheritClassPath = true).map { ClassFile(it.key, it.value) }
        val (library, rest) = classes.partition { it.name.startsWith("p.Finder") }
        val (project, tests) = rest.partition { it.name == "p.Calc" }
        val jar = writeJar(dir.resolve("library.jar"), library.associate { it.name to it.bytes })
        val mainDir = writeClasses(dir.resolve("main"), project)
        return Layout(project, tests, mainDir, writeClasses(dir.resolve("test"), tests), jar)
    }

    private fun mutant() = mutantOf("p.Calc")

    private fun mutated() = mutantBytes("package p\n$CALC_MUTANT", "p.Calc")

    @Test
    fun `a library resolves project classes and scans their packages, and its statics do not outlive a run`() {
        val layout = layout()
        val classpath = listOf(layout.mainDir, layout.testDir, layout.library) + runtimeJars()
        val executor = JUnitTestExecutor(layout.project, layout.tests, classpath).also(closeables::add)
        assertTrue(executor.run(mutant(), mutated(), deadline) is Outcome.Killed)
        assertEquals(Outcome.Survived, executor.baseline(deadline).outcome)
    }

    @Test
    fun `the forked child resolves project classes from libraries, scans the class directories and isolates runs`() {
        val layout = layout()
        val settings =
            ForkSettings(
                Path.of(System.getProperty("java.home"), "bin", "java"),
                emptyList(),
                emptyMap(),
                null,
                layout.mainDir,
                layout.testDir,
                runtimeJars() + listOf(layout.library),
                1,
            )
        val executor = ForkedTestExecutor(settings).also(closeables::add)
        assertTrue(executor.run(mutant(), mutated(), deadline) is Outcome.Killed)
        assertEquals(Outcome.Survived, executor.baseline(deadline).outcome)
        assertEquals(1, executor.startedChildren)
    }

    private companion object {
        val SOURCE =
            """
            package p
            $IMPORTS
            $CALC

            object Finder {
                private var first: Class<*>? = null

                fun find(name: String): Class<*> = Class.forName(name)

                fun firstFound(name: String): Class<*> = first ?: find(name).also { first = it }
            }

            class CalcTest {
                @Test fun libraryFindsTheTestClass() = assertSame(CalcTest::class.java, Finder.find("p.CalcTest"))

                @Test fun libraryStaticsHoldThisRunsCalc() {
                    val calc = Finder.firstFound("p.Calc")
                    val add = calc.getMethod("add", Int::class.java, Int::class.java)
                    assertEquals(3, add.invoke(calc.getDeclaredConstructor().newInstance(), 1, 2))
                }

                @Test fun codeSourcesAreTheClassDirectories() {
                    val test = java.nio.file.Path.of(CalcTest::class.java.protectionDomain.codeSource.location.toURI())
                    val main = java.nio.file.Path.of(Calc::class.java.protectionDomain.codeSource.location.toURI())
                    assertEquals("test", test.fileName.toString())
                    assertEquals("main", main.fileName.toString())
                }

                @Test fun packageScanFindsTheClassDirectories() {
                    val loader = Thread.currentThread().contextClassLoader
                    assertNotNull(loader.getResource("p/"))
                    val found = loader.getResources("p/").toList().map { java.nio.file.Path.of(it.toURI()) }
                    assertTrue(found.any { java.nio.file.Files.exists(it.resolve("Calc.class")) }, found.toString())
                    assertTrue(found.any { java.nio.file.Files.exists(it.resolve("CalcTest.class")) }, found.toString())
                }
            }
            """.trimIndent()
    }
}
