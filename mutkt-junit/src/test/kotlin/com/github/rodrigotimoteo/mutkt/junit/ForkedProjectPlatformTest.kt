package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.time.Duration.Companion.seconds

// Only one Platform version is available offline, so the project's copy is told apart by its jar file name.
@Timeout(value = 120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ForkedProjectPlatformTest {
    @TempDir
    lateinit var dir: Path

    private var executor: ForkedTestExecutor? = null

    @AfterEach
    fun closeExecutor() {
        executor?.close()
    }

    private fun isLauncher(jar: Path) = jar.fileName.toString().startsWith("junit-platform-launcher")

    private fun run(
        classpath: List<Path>,
        test: String,
    ): Outcome {
        val pool = ForkedTestExecutor(forkSettingsFor(dir, test, classpath)).also { executor = it }
        return pool.run(mutantOf(), mutantBytes(CALC), 60.seconds)
    }

    private fun launcherSource(expected: Path) =
        "class CalcTest { @Test fun source() { " +
            "val location = org.junit.platform.launcher.Launcher::class.java.protectionDomain.codeSource.location; " +
            "assertEquals(\"${expected.fileName}\", java.nio.file.Path.of(location.toURI()).fileName.toString()) } }"

    @Test
    fun `the child runs the project's own launcher jar`() {
        val jars = runtimeJars()
        val target = dir.resolve("project-launcher.jar")
        val projectLauncher = Files.copy(jars.first(::isLauncher), target, REPLACE_EXISTING)
        val classpath = jars.filterNot(::isLauncher) + listOf(projectLauncher)
        assertEquals(Outcome.Survived, run(classpath, launcherSource(projectLauncher)))
    }

    @Test
    fun `a project without the launcher runs on the bundled one`() {
        val bundled = runtimeJars().first(::isLauncher)
        assertEquals(Outcome.Survived, run(runtimeJars().filterNot(::isLauncher), launcherSource(bundled)))
    }
}
