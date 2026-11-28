package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.gradle.TestKitProject.Companion.calc
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/** The result history on a real build with the configuration cache on. */
@Tag("e2e")
@Tag("matrix")
@Timeout(180)
class MutKtHistoryTest {
    @TempDir
    lateinit var dir: File

    private fun TestKitProject.mutants() = Regex("Mutants: (\\d+)").find(report.readText())!!.groupValues[1].toInt()

    // A trailing comment changes the sources, an input, so the task runs again on byte-identical classes.
    private fun TestKitProject.touchSources(n: Int) = write("src/main/java/demo/Calc.java", calc("a + b") + "// $n\n")

    private fun jar(text: String) =
        JarOutputStream(dir.resolve("libs/extra.jar").apply { parentFile.mkdirs() }.outputStream()).use { jar ->
            jar.putNextEntry(ZipEntry("extra.txt"))
            jar.write(text.toByteArray())
        }

    @OnGradleVersions
    fun `a second run reuses every result and a classpath change reuses none`(version: String) {
        val project = TestKitProject(dir, version)
        jar("one")
        project.setUp("dependencies { testRuntimeOnly files('libs/extra.jar') }")
        val first = project.build()
        val total = project.mutants()
        assertTrue(total > 0 && "reused" !in project.report.readText(), project.report.readText())
        assertTrue(dir.resolve("build/mutkt/history.json").isFile, first.output)

        project.touchSources(1)
        val second = project.build()
        assertEquals(TaskOutcome.SUCCESS, second.task(":mutkt")?.outcome, second.output)
        assertTrue("Reusing configuration cache" in second.output, second.output)
        assertTrue("Mutants: $total ($total reused)" in project.report.readText(), project.report.readText())

        jar("two")
        project.build()
        assertTrue("Mutants: $total\n" in project.report.readText(), project.report.readText())
    }

    @OnGradleVersions
    fun `a corrupt history fails the run with one clear message`(version: String) {
        val project = TestKitProject(dir, version)
        project.setUp()
        project.write("build/mutkt/history.json", "{not json")

        val result = project.buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":mutkt")?.outcome, result.output)
        // The temporary directory may be reached through a symbolic link, so only the end of the path is fixed.
        assertTrue(
            Regex("The MutKt result history \\S+/build/mutkt/history.json is corrupt").containsMatchIn(result.output),
            result.output,
        )
        assertTrue("delete it and run again" in result.output, result.output)
    }
}
