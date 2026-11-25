package com.github.rodrigotimoteo.mutkt.junit.fork

import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import com.github.rodrigotimoteo.mutkt.junit.CALC
import com.github.rodrigotimoteo.mutkt.junit.CALC_MUTANT
import com.github.rodrigotimoteo.mutkt.junit.IMPORTS
import com.github.rodrigotimoteo.mutkt.junit.mutantBytes
import com.github.rodrigotimoteo.mutkt.junit.mutantOf
import com.github.rodrigotimoteo.mutkt.junit.runtimeJars
import com.github.rodrigotimoteo.mutkt.junit.splitClasses
import com.github.rodrigotimoteo.mutkt.junit.writeClasses
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

// Runs the real entry point in a child JVM, as the parent pool will.
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ForkMainProcessTest {
    @TempDir
    lateinit var dir: Path

    private var process: Process? = null

    @AfterEach
    fun killChild() {
        process?.destroyForcibly()?.waitFor(5, TimeUnit.SECONDS)
    }

    private val noisyTest =
        "class CalcTest { @Test fun adds() { println(\"noise\"); assertEquals(5, Calc().add(2, 3)) } }"

    private fun startup(lines: List<String>): Path = Files.write(dir.resolve("startup.properties"), lines)

    private fun validStartup(): Path {
        val (project, tests) = splitClasses("$IMPORTS$CALC\n$noisyTest")
        val projectDir = writeClasses(dir.resolve("main"), project)
        val testDir = writeClasses(dir.resolve("test"), tests)
        return startup(
            listOf("projectClassDir=$projectDir", "testClassDir=$testDir", "engine=junit-jupiter") +
                runtimeJars().map { "classpath=$it" },
        )
    }

    private fun launch(startupFile: Path): Process {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        return ProcessBuilder(
            java,
            "-cp",
            System.getProperty("java.class.path"),
            "com.github.rodrigotimoteo.mutkt.junit.fork.ForkMain",
            startupFile.toString(),
        ).redirectError(dir.resolve("child.err").toFile())
            .start()
            .also { process = it }
    }

    private fun Process.exit(): Int {
        check(waitFor(20, TimeUnit.SECONDS)) { "child did not exit" }
        return exitValue()
    }

    @Test
    fun `serves a run with noisy tests, then exits 0 on shutdown`() {
        val child = launch(validStartup())
        val out = DataOutputStream(child.outputStream.buffered())
        val input = DataInputStream(child.inputStream.buffered())
        assertEquals(ForkReply.Ready(), readReply(input))

        writeRequest(out, ForkRequest.Run(mutantOf(), mutantBytes(CALC_MUTANT), 20_000))
        assertEquals(ForkReply.Started, readReply(input))
        assertEquals(ForkReply.Result(Outcome.Killed("CalcTest.adds")), readReply(input))
        writeRequest(out, ForkRequest.Run(mutantOf(), mutantBytes(CALC), 20_000))
        assertEquals(ForkReply.Started, readReply(input))
        assertEquals(ForkReply.Result(Outcome.Survived), readReply(input))

        writeRequest(out, ForkRequest.Shutdown)
        assertEquals(0, child.exit())
    }

    @Test
    fun `closing stdin ends the child with 0`() {
        val child = launch(validStartup())
        assertEquals(ForkReply.Ready(), readReply(DataInputStream(child.inputStream.buffered())))
        child.outputStream.close()
        assertEquals(0, child.exit())
    }

    @Test
    fun `a malformed frame exits 2`() {
        val child = launch(validStartup())
        assertEquals(ForkReply.Ready(), readReply(DataInputStream(child.inputStream.buffered())))
        child.outputStream.write(byteArrayOf(0, 0, 0, 0))
        child.outputStream.flush()
        assertEquals(2, child.exit())
    }

    @Test
    fun `a bad startup file exits 3`() {
        assertEquals(3, launch(startup(listOf("unknown=1"))).exit())
    }
}
