package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

// The fork code is compiled and bundled with one Platform version but must run on the project's: this runs it on a
// second one (the platformCompatTest task resolves it over the network, so the test is opt-in).
@Tag("platformCompat")
@Timeout(value = 120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ForkedPlatformCompatTest {
    @TempDir
    lateinit var dir: Path

    private var executor: ForkedTestExecutor? = null

    @AfterEach
    fun closeExecutor() {
        executor?.close()
    }

    private val otherPlatform: List<Path> =
        checkNotNull(System.getProperty("mutkt.otherPlatform")) { "Run through the platformCompatTest task" }
            .split(File.pathSeparator)
            .map(Path::of)

    private fun isPlatformJar(jar: Path) = jar.fileName.toString().let { it.startsWith("junit-") || "opentest4j" in it }

    private val projectJars = runtimeJars().filterNot(::isPlatformJar) + otherPlatform

    private fun pool(test: String) = ForkedTestExecutor(forkSettingsFor(dir, test, projectJars)).also { executor = it }

    private fun jarOf(prefix: String) = otherPlatform.first { it.fileName.toString().startsWith(prefix) }.fileName

    @Test
    fun `the child runs the tests on the other Platform and Jupiter versions`() {
        val launcher = jarOf("junit-platform-launcher")
        val jupiter = jarOf("junit-jupiter-engine")
        val test =
            "class CalcTest { @Test fun adds() { assertEquals(5, Calc().add(2, 3)) } " +
                "@Test fun versions() { " +
                "assertEquals(\"$launcher\", jarName(org.junit.platform.launcher.Launcher::class.java)); " +
                "assertEquals(\"$jupiter\", jarName(org.junit.jupiter.engine.JupiterTestEngine::class.java)) } " +
                "private fun jarName(type: Class<*>) = " +
                "java.nio.file.Path.of(type.protectionDomain.codeSource.location.toURI()).fileName.toString() }"
        val pool = pool(test)

        assertEquals(Outcome.Survived, pool.run(mutantOf(), mutantBytes(CALC), 60.seconds))
        assertEquals(Outcome.Killed("CalcTest.adds"), pool.run(mutantOf(), mutantBytes(), 60.seconds))
    }
}
