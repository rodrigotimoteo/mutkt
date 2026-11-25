package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.AnalysisConfig
import com.github.rodrigotimoteo.mutkt.application.ClassFilters
import com.github.rodrigotimoteo.mutkt.application.RunMutationAnalysis
import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.asm.AsmMutantGenerator
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus.KILLED
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus.SURVIVED
import com.github.rodrigotimoteo.mutkt.domain.MutantStatus.TIMEOUT
import com.github.rodrigotimoteo.mutkt.domain.MutationReport
import com.github.rodrigotimoteo.mutkt.junit.JUnitTestExecutor
import com.github.rodrigotimoteo.mutkt.report.ConsoleReportWriter
import com.github.rodrigotimoteo.mutkt.report.FileSourceLookup
import com.github.rodrigotimoteo.mutkt.testing.FakeClassSource
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds

/**
 * P1 pipeline without Gradle: real Kotlin compiler output, ASM generator, JUnit executor, use case and console writer.
 *
 * Exactly one fixture mutant (sumTo: `i += step` becomes `i -= step`) never terminates. Its worker thread is
 * abandoned after the deadline and keeps spinning until the JVM exits (ROD-104 would fork a JVM to kill it), so
 * this class runs only in the separate e2eTest task, never in the shared unit-test JVM.
 */
@Tag("e2e")
class EndToEndTest {
    @Test
    fun `analysis of the compiled fixture reports every mutant and matches the golden report`() {
        val compiled =
            compileKotlin(resource("Fixture.kt.txt"), inheritClassPath = true).map { ClassFile(it.key, it.value) }
        val (tests, production) = compiled.partition { it.name.endsWith("Test") }
        val sources = Files.createTempDirectory("mutkt-e2e-src")
        val console = StringBuilder()
        try {
            writeSource(sources)
            val report = analyse(production, tests, console, sources)
            assertEquals(EXPECTED, report.results.map { key(it.mutant) to it.status })
            assertGolden(console.toString())
        } finally {
            sources.toFile().deleteRecursively()
        }
    }

    private fun analyse(
        production: List<ClassFile>,
        tests: List<ClassFile>,
        console: StringBuilder,
        sources: Path,
    ): MutationReport =
        JUnitTestExecutor(production, tests, runtimeJars()).use { executor ->
            RunMutationAnalysis(
                FakeClassSource(production),
                AsmMutantGenerator(MVP_OPERATORS, ::systemClass),
                executor,
                ConsoleReportWriter(console, FileSourceLookup(listOf(sources))),
                AnalysisConfig(ClassFilters(), 10.seconds, parallelism = 2),
            ).run()
        }

    // The fixture compiles from Source.kt in package a.b.
    private fun writeSource(root: Path) {
        val file = root.resolve("a/b/Source.kt")
        Files.createDirectories(file.parent)
        Files.writeString(file, resource("Fixture.kt.txt"))
    }

    private fun resource(name: String): String =
        checkNotNull(EndToEndTest::class.java.getResourceAsStream("/e2e/$name")) { "Missing resource $name" }
            .use { String(it.readBytes(), Charsets.UTF_8) }

    private fun key(mutant: Mutant): String =
        "${mutant.operator} ${mutant.className.substringAfterLast('.')}.${mutant.methodName} " +
            "#${mutant.id.ordinal} ${mutant.description}"

    private fun assertGolden(actual: String) {
        if (System.getProperty("mutkt.updateGolden") == "true") {
            Files.writeString(Paths.get(System.getProperty("mutkt.goldenDir"), GOLDEN), actual)
            return
        }
        assertEquals(
            resource(GOLDEN),
            actual,
            "Console report differs from the golden; after reviewing it, regenerate with " +
                "./gradlew :mutkt-gradle:e2eTest -Dmutkt.updateGolden=true",
        )
    }

    private companion object {
        const val GOLDEN = "report.golden.txt"

        // Pinned so that registering further operators does not change the hand-computed expectations below.
        val MVP_OPERATORS =
            setOf(
                "negate-conditionals",
                "arithmetic",
                "conditionals-boundary",
                "null-returns",
                "return-vals",
                "empty-returns",
            )

        // Compiler jars (kctfork is the jar named core-<version>) serve compileKotlin only, not the code under test.
        val COMPILER_JARS = Regex("kotlin-compiler-embeddable|^core-\\d")

        // Hand-computed from the fixture, in discovery order (class name, then operator order, then bytecode order).
        // "ifle" in isPositive and "if_icmplt" in grade are what `n > 0` and `score >= 60` compile to (jump to the else
        // branch). The one SURVIVED boundary per method is the weak test (isPositive) or the untested edge (grade 60).
        // sumTo's second ladd is `i += step`: i then decreases forever, so it is the TIMEOUT mutant.
        // doubled has no negate-conditionals mutant: its only IFEQ is the hasNext loop test of the inlined `map`,
        // compiler noise removed by InlinedBodyFilter (ROD-116). So 16 mutants: 13 KILLED, 2 SURVIVED, 1 TIMEOUT,
        // score (13 + 1) / 16 = 87.5%; Calculator 10/11 = 90.9%, Grader 4/5 = 80.0%.
        val EXPECTED: List<Pair<String, MutantStatus>> =
            listOf(
                // IsPositive(5) turns false; positiveNumberIsPositive
                "negate-conditionals Calculator.isPositive #0 ifle -> ifgt" to KILLED,
                // The loop never runs, result is 0; sumsWithStep
                "negate-conditionals Calculator.sumTo #0 ifge -> iflt" to KILLED,
                // 2 - 3 is -1; adds
                "arithmetic Calculator.add #0 iadd -> isub" to KILLED,
                // Yields [0, 1, 1]; doublesEveryValue
                "arithmetic Calculator.doubled #0 imul -> idiv" to KILLED,
                // Total -= i gives -6; sumsWithStep
                "arithmetic Calculator.sumTo #0 ladd -> lsub" to KILLED,
                "arithmetic Calculator.sumTo #1 ladd -> lsub" to TIMEOUT,
                "conditionals-boundary Calculator.isPositive #0 ifle -> iflt" to SURVIVED,
                // I == n is included, total 10; sumsWithStep
                "conditionals-boundary Calculator.sumTo #0 ifge -> ifgt" to KILLED,
                // Add returns 0; adds
                "return-vals Calculator.add #0 ireturn -> 0" to KILLED,
                // SumTo returns 0; sumsWithStep
                "return-vals Calculator.sumTo #0 lreturn -> 0" to KILLED,
                // Doubled returns []; doublesEveryValue
                "empty-returns Calculator.doubled #0 areturn -> empty" to KILLED,
                // The 90 test falls through to a lower band; gradesBands
                "negate-conditionals Grader.grade #0 if_icmplt -> if_icmpge" to KILLED,
                // The 60 test falls through to a lower band; gradesBands
                "negate-conditionals Grader.grade #1 if_icmplt -> if_icmpge" to KILLED,
                // Grade(90) becomes B; gradesBands
                "conditionals-boundary Grader.grade #0 if_icmplt -> if_icmple" to KILLED,
                "conditionals-boundary Grader.grade #1 if_icmplt -> if_icmple" to SURVIVED,
                // Grade returns null; gradesBands
                "null-returns Grader.grade #0 areturn -> null" to KILLED,
            )

        // The test JVM's jars, as the Gradle task will pass them. Directories are left out on purpose: they hold
        // this module's own classes and the other modules' build output, while the fixture exists only as bytes.
        fun runtimeJars(): List<Path> =
            System
                .getProperty("java.class.path")
                .split(File.pathSeparator)
                .map(Paths::get)
                .filter(Files::isRegularFile)
                .filterNot { COMPILER_JARS.containsMatchIn(it.fileName.toString()) }

        // Dependency types (Kotlin stdlib, Jupiter) for frame computation come from the test JVM's own classpath.
        fun systemClass(internalName: String): ByteArray? =
            ClassLoader.getSystemResourceAsStream("$internalName.class")?.use { it.readBytes() }
    }
}
