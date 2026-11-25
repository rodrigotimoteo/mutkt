package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Runs the task action in this JVM against real compiler output and a real child JVM pool. */
@Tag("e2e")
class MutKtTaskRunTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `analyses the compiled classes in child JVMs and summarises the result`() {
        val compiled = compileKotlin(FIXTURE, inheritClassPath = true)
        val main = dir.resolve("main").also { write(it, compiled.filterKeys { name -> !name.endsWith("Test") }) }
        val tests = dir.resolve("test").also { write(it, compiled.filterKeys { name -> name.endsWith("Test") }) }
        val sources = dir.resolve("src").also { it.resolve("demo").mkdirs() }
        sources.resolve("demo/Calc.kt").writeText(FIXTURE)
        val task = task(main, tests, sources)

        val result = task.runAnalysis()
        val summary = result.summary

        assertEquals(1.0, result.score)

        assertTrue("Mutants: 2" in summary, summary)
        assertTrue("KILLED       2" in summary, summary)
        assertTrue("Score: 100.0%" in summary, summary)
    }

    @Test
    fun `excluded classes are not mutated`() {
        val compiled = compileKotlin(FIXTURE, inheritClassPath = true)
        val main = dir.resolve("main").also { write(it, compiled.filterKeys { name -> !name.endsWith("Test") }) }
        val tests = dir.resolve("test").also { write(it, compiled.filterKeys { name -> name.endsWith("Test") }) }
        val task = task(main, tests, dir.resolve("none"))
        task.excludedClasses.add("demo.Calc")

        val result = task.runAnalysis()

        assertTrue("Mutants: 0" in result.summary)
        assertNull(result.score)
    }

    @Test
    fun `selected operators restrict the mutants`() {
        val task = fixtureTask()
        task.operators.add("arithmetic")

        val summary = task.runAnalysis().summary

        assertTrue(Regex("^Mutants: 1$", RegexOption.MULTILINE).containsMatchIn(summary), summary)
    }

    @Test
    fun `an unknown operator fails before any test runs`() {
        val task = fixtureTask()
        task.operators.add("nope")

        val error = assertThrows<GradleException> { task.runAnalysis() }

        assertTrue("Unknown operator 'nope'" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `a wrong engine id fails the baseline with the cause and writes no report`() {
        val task = fixtureTask()
        task.engines.set(setOf("junit-vintage"))

        val error = assertThrows<IllegalStateException> { task.analyse() }

        assertTrue("running the baseline" in error.message.orEmpty(), error.message)
        assertTrue("No TestEngine ID matched" in error.message.orEmpty(), error.message)
        assertFalse(
            task.reportsDir
                .get()
                .asFile
                .resolve("mutkt.txt")
                .exists(),
        )
    }

    private fun fixtureTask(): MutKtTask {
        val compiled = compileKotlin(FIXTURE, inheritClassPath = true)
        val main = dir.resolve("main").also { write(it, compiled.filterKeys { name -> !name.endsWith("Test") }) }
        val tests = dir.resolve("test").also { write(it, compiled.filterKeys { name -> name.endsWith("Test") }) }
        return task(main, tests, dir.resolve("none"))
    }

    private fun task(
        main: File,
        tests: File,
        sources: File,
    ): MutKtTask {
        val project: Project = ProjectBuilder.builder().withProjectDir(dir.resolve("project")).build()
        project.pluginManager.apply("java")
        project.pluginManager.apply(MutKtPlugin::class.java)
        val task = project.tasks.getByName("mutkt") as MutKtTask
        task.classesDirs.setFrom(main)
        task.testClassesDirs.setFrom(tests)
        task.sourceDirs.setFrom(sources)
        task.testRuntimeClasspath.setFrom(main, tests, runtimeJars())
        return task
    }

    private fun write(
        root: File,
        classes: Map<String, ByteArray>,
    ) = classes.forEach { (name, bytes) ->
        root.resolve(name.replace('.', '/') + ".class").apply { parentFile.mkdirs() }.writeBytes(bytes)
    }

    private fun runtimeJars(): List<File> =
        System
            .getProperty("java.class.path")
            .split(File.pathSeparator)
            .map(::File)
            .filter { it.isFile && PROJECT_JARS.containsMatchIn(it.name) }

    private companion object {
        // What the fixture's tests need at run time; the Launcher API comes from the plugin side.
        val PROJECT_JARS =
            Regex("^(junit-jupiter|junit-platform-(engine|commons)|opentest4j|apiguardian|kotlin-stdlib)")

        val FIXTURE =
            """
            package demo

            import org.junit.jupiter.api.Assertions.assertEquals
            import org.junit.jupiter.api.Test

            class Calc {
                fun add(a: Int, b: Int): Int = a + b
            }

            class CalcTest {
                @Test
                fun adds() = assertEquals(5, Calc().add(2, 3))
            }
            """.trimIndent()
    }
}
