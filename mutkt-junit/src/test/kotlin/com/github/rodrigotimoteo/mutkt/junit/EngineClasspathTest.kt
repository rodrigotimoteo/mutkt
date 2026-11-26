package com.github.rodrigotimoteo.mutkt.junit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EngineClasspathTest {
    @TempDir
    lateinit var dir: Path

    private fun jar(
        name: String,
        vararg entries: String,
    ): Path =
        dir.resolve(name).also { path ->
            ZipOutputStream(Files.newOutputStream(path)).use { zip ->
                entries.forEach { zip.putNextEntry(ZipEntry(it)) }
            }
        }

    private fun bundled(suffix: String = "bundled") =
        listOf(
            jar("launcher-$suffix.jar", "org/junit/platform/launcher/core/LauncherFactory.class"),
            jar("engine-$suffix.jar", "org/junit/platform/engine/TestEngine.class"),
            jar("commons-$suffix.jar", "org/junit/platform/commons/JUnitException.class"),
            jar("opentest4j-$suffix.jar", "org/opentest4j/AssertionFailedError.class"),
        )

    private val own by lazy { listOf(jar("mutkt.jar", "mutkt/ForkedTestExecutor.class")) }

    @Test
    fun `the project's platform jars replace the bundled ones, placed last`() {
        val project = bundled("project")
        val result = engineClasspathFor(own + bundled(), project + jar("app.jar", "App.class"))
        assertEquals(own + project, result)
    }

    @Test
    fun `a project without the launcher keeps the bundled platform, even with its own engine`() {
        val engine = own + bundled()
        val project = listOf(bundled("project")[1], jar("jupiter.jar", "org/junit/jupiter/api/Test.class"))
        assertEquals(engine, engineClasspathFor(engine, project))
    }

    @Test
    fun `a platform piece the project lacks still comes from the bundled jars`() {
        val bundled = bundled()
        val project = bundled("project").take(3)
        assertEquals(own + project + listOf(bundled[3]), engineClasspathFor(own + bundled, project))
    }

    @Test
    fun `an unreadable jar fails naming the file`() {
        val corrupt = dir.resolve("broken.jar").also { Files.writeString(it, "not a zip") }
        val error = assertThrows(IllegalStateException::class.java) { engineClasspathFor(own, listOf(corrupt)) }
        assertTrue(error.message.orEmpty().contains(corrupt.toString()), error.message)
    }

    @Test
    fun `class directories are searched too`() {
        val launcherDir = Files.createDirectories(dir.resolve("classes/org/junit/platform/launcher/core"))
        Files.createFile(launcherDir.resolve("LauncherFactory.class"))
        val project = dir.resolve("classes")
        assertEquals(own + listOf(project) + bundled().drop(1), engineClasspathFor(own + bundled(), listOf(project)))
    }

    @Test
    fun `a vintage run of a project without the vintage engine gets the bundled one, last`() {
        val project = listOf(jar("junit.jar", "junit/framework/Test.class"))
        val result = projectClasspathFor(listOf("junit-jupiter", "junit-vintage"), project)
        assertEquals(project, result.dropLast(1))
        assertTrue(
            result
                .last()
                .fileName
                .toString()
                .startsWith("junit-vintage-engine"),
            result.toString(),
        )
    }

    @Test
    fun `a vintage run of a project with its own platform but no vintage engine fails naming the dependency`() {
        val project = bundled("project") + jar("junit.jar", "junit/framework/Test.class")
        val error =
            assertThrows(IllegalArgumentException::class.java) { projectClasspathFor(listOf("junit-vintage"), project) }
        assertTrue("org.junit.vintage:junit-vintage-engine" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `the project's own vintage engine is not doubled`() {
        val project = listOf(jar("vintage.jar", "org/junit/vintage/engine/VintageTestEngine.class"))
        assertEquals(project, projectClasspathFor(listOf("junit-vintage"), project))
    }

    @Test
    fun `a run without the vintage engine keeps the project classpath`() {
        val project = listOf(jar("app.jar", "App.class"))
        assertEquals(project, projectClasspathFor(listOf("junit-jupiter"), project))
    }
}
