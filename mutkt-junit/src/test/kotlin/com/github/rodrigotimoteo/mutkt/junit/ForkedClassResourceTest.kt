package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.Outcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

// A test that reads its own bytecode must see what the fork defined, not a parent copy.
@Timeout(value = 120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ForkedClassResourceTest {
    @TempDir
    lateinit var dir: Path

    private var executor: ForkedTestExecutor? = null

    @AfterEach
    fun closeExecutor() {
        executor?.close()
    }

    private val calc = "package p\n$CALC"

    private fun mutantOfCalc() = mutantOf("p.Calc")

    private fun bytesOf(source: String) = mutantBytes(source, "p.Calc")

    // Every lookup style must return the file named by -Dexpected.
    private val test =
        """
        class CalcTest {
            @Test fun resources() {
                val expected = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(System.getProperty("expected")))
                assertArrayEquals(expected, Calc::class.java.getResourceAsStream("Calc.class")!!.use { it.readBytes() })
                assertArrayEquals(expected, Calc::class.java.getResourceAsStream("/p/Calc.class")!!.use { it.readBytes() })
                assertArrayEquals(expected, Calc::class.java.classLoader.getResourceAsStream("p/Calc.class")!!.use { it.readBytes() })
                assertArrayEquals(expected, Calc::class.java.getResource("Calc.class")!!.readBytes())
                assertEquals(1, Calc::class.java.classLoader.getResources("p/Calc.class").toList().size)
            }
        }
        """.trimIndent()

    private fun run(
        expected: ByteArray,
        run: ByteArray,
        parentCopy: ByteArray? = null,
    ): Outcome {
        val (project, tests) = splitClasses("package p\n$IMPORTS$CALC\n$test", setOf("p.Calc"))
        val expectedFile = Files.write(dir.resolve("expected"), expected)
        val stale = parentCopy?.let { writeClasses(dir.resolve("stale"), listOf(ClassFile("p.Calc", it))) }
        val settings =
            ForkSettings(
                Path.of(System.getProperty("java.home"), "bin", "java"),
                listOf("-Dexpected=$expectedFile"),
                emptyMap(),
                null,
                writeClasses(dir.resolve("main"), project),
                writeClasses(dir.resolve("test"), tests),
                runtimeJars() + listOfNotNull(stale),
                1,
            )
        val pool = ForkedTestExecutor(settings).also { executor = it }
        return pool.run(mutantOfCalc(), run, 60.seconds)
    }

    @Test
    fun `the original class reads its own bytes`() {
        val original = bytesOf(calc)
        assertEquals(Outcome.Survived, run(original, original))
    }

    @Test
    fun `a mutated class reads the mutant bytes`() {
        val mutant = bytesOf(calc.replace("a + b", "a - b"))
        assertEquals(Outcome.Survived, run(mutant, mutant))
    }

    @Test
    fun `a same-named parent copy does not win`() {
        val stale = bytesOf(calc.replace("a + b", "a * b"))
        val mutant = bytesOf(calc.replace("a + b", "a - b"))
        assertEquals(Outcome.Survived, run(mutant, mutant, parentCopy = stale))
    }
}
