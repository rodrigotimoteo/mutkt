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
import org.junit.jupiter.api.Timeout
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
        sources.resolve("demo/Source.kt").writeText(FIXTURE)
        val task = task(main, tests, sources)

        val result = task.runAnalysis()
        val summary = result.summary

        assertEquals(1.0, result.score)

        assertTrue("Mutants: 2" in summary, summary)
        assertTrue("KILLED       2" in summary, summary)
        assertTrue("Score: 100.0%" in summary, summary)
        assertTrue("\"schemaVersion\": \"2\"" in result.json && "\"demo/Source.kt\"" in result.json, result.json)
        assertTrue("\"status\": \"Killed\"" in result.json && "class Calc" in result.json, result.json)
        // The bundle fills most of the page; its tail holds the report data.
        val page = result.html.takeLast(PAGE_TAIL)
        assertTrue("<mutation-test-report-app>" in result.html, "No viewer element in the HTML report: $page")
        assertTrue("\"demo/Source.kt\"" in result.html, "No demo/Source.kt entry in the HTML report: $page")
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

    // Without a deadline derived from the fast baseline, the endless mutant would run for timeoutMs (5 minutes).
    @Test
    @Timeout(value = 90, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `an endless mutant of a fast suite times out long before timeoutMs`() {
        val task = fixtureTask(LOOP)
        task.operators.add("arithmetic")
        task.timeoutMs.set(300_000L)

        val json = task.runAnalysis().json

        val statuses =
            STATUS
                .findAll(json)
                .map { it.groupValues[1] }
                .sorted()
                .toList()
        assertEquals(listOf("Killed", "Timeout"), statuses, json)
    }

    @Test
    fun `a mutant that exits its JVM while the tests run is killed, named by the exit code`() {
        val task = fixtureTask(EXITING)
        task.operators.addAll("arithmetic", "negate-conditionals")

        val result = task.runAnalysis()

        assertTrue("KILLED       2" in result.summary && "ERROR        0" in result.summary, result.summary)
        assertTrue("\"statusReason\": \"Killed by JVM exit 7\"" in result.json, result.json)
    }

    @Test
    fun `a JVM exit before the tests start is an ERROR, and when every mutant errs the task fails after the report`() {
        val task = fixtureTask(EXITING_EARLY)
        task.operators.add("negate-conditionals")

        val error = assertThrows<GradleException> { task.analyse() }

        assertTrue("All 1 mutants ended in ERROR" in error.message.orEmpty(), error.message)
        val report =
            task.reportsDir
                .get()
                .asFile
                .resolve("mutkt.txt")
                .readText()
        assertTrue(
            Regex(
                "^Errors \\(1\\)\\nnegate-conditionals:demo\\.Calc#$LONG\\(II\\)I:0: " +
                    "Forked test JVM failed \\(exit code 7\\)",
                RegexOption.MULTILINE,
            ).containsMatchIn(report),
            report,
        )
    }

    @Test
    fun `a class of another source set with the name of a main class fails the run`() {
        val task = fixtureTask()
        val support = dir.resolve("support")
        dir.resolve("main").copyRecursively(support)
        task.projectSupportClassesDirs.setFrom(support)

        val failure = assertThrows<GradleException> { task.runAnalysis() }

        assertTrue("both in main and in another source set" in failure.message.orEmpty(), failure.message)
        assertTrue("demo/Calc.class" in failure.message.orEmpty(), failure.message)
    }

    private fun fixtureTask(source: String = FIXTURE): MutKtTask {
        val compiled = compileKotlin(source, inheritClassPath = true)
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
        const val PAGE_TAIL = 2000

        val STATUS = Regex(""""status": "(\w+)"""")

        // What the fixture's tests need at run time; the Launcher API comes from the plugin side.
        val PROJECT_JARS =
            Regex("^(junit-jupiter|junit-platform-(engine|commons)|opentest4j|apiguardian|kotlin-stdlib)")

        // A long id must not push the exit code out of the capped cause.
        const val LONG = "addNumbersWithAVeryLongMethodNameToProveTheCapKeepsTheExitCode"

        // `i += step` mutated to `i -= step` never reaches n; a Long counter cannot wrap around in time to end it.
        val LOOP =
            """
            package demo

            import org.junit.jupiter.api.Assertions.assertEquals
            import org.junit.jupiter.api.Test

            class Loop {
                fun sum(n: Long, step: Long): Long {
                    var total = 0L
                    var i = 0L
                    while (i < n) {
                        total += i
                        i += step
                    }
                    return total
                }
            }

            class LoopTest {
                @Test
                fun sums() = assertEquals(6L, Loop().sum(4, 1))
            }
            """.trimIndent()

        // Negating the guard makes the mutant call System.exit while the tests run: a kill.
        val EXITING =
            """
            package demo

            import org.junit.jupiter.api.Assertions.assertEquals
            import org.junit.jupiter.api.Test

            class Calc {
                fun $LONG(a: Int, b: Int): Int {
                    if (a < 0) System.exit(7)
                    return a + b
                }
            }

            class CalcTest {
                @Test
                fun adds() = assertEquals(5, Calc().$LONG(2, 3))
            }
            """.trimIndent()

        // Jupiter builds the generator while it discovers CalcTest, so the negated guard exits before the tests start.
        val EXITING_EARLY =
            """
            package demo

            import org.junit.jupiter.api.Assertions.assertEquals
            import org.junit.jupiter.api.DisplayNameGeneration
            import org.junit.jupiter.api.DisplayNameGenerator
            import org.junit.jupiter.api.Test

            class Calc {
                fun $LONG(a: Int, b: Int): Int {
                    if (a < 0) System.exit(7)
                    return a + b
                }
            }

            class NamesTest : DisplayNameGenerator.Standard() {
                init {
                    Calc().$LONG(2, 3)
                }
            }

            @DisplayNameGeneration(NamesTest::class)
            class CalcTest {
                @Test
                fun adds() = assertEquals(5, Calc().$LONG(2, 3))
            }
            """.trimIndent()

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
