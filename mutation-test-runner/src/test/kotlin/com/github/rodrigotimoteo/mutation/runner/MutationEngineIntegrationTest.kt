package com.github.rodrigotimoteo.mutation.runner

import com.github.rodrigotimoteo.mutation.engine.MutationEngine
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertTrue

/**
 * Integration test that runs the full mutation testing pipeline
 * against the sample Calculator class.
 */
class MutationEngineIntegrationTest {
    private val projectRoot =
        File(System.getProperty("user.dir")).let { dir ->
            // user.dir may be the module directory, go up to project root
            if (dir.name == "mutation-test-runner") dir.parentFile else dir
        }
    private val buildDir = File(projectRoot, "mutation-sample/build")

    @Test
    fun `engine should generate and test mutations for sample project`() {
        // Locate compiled classes
        val classesDir = findClassesDir()
        val testClassesDir = findTestClassesDir()

        assertTrue(classesDir.exists(), "Classes directory not found: $classesDir")
        assertTrue(testClassesDir.exists(), "Test classes directory not found: $testClassesDir")

        // Load Calculator class bytes
        val calculatorClassPath = classesDir.toPath().resolve("com/github/rodrigotimoteo/mutation/sample/Calculator.class")
        val calculatorClassFile = calculatorClassPath.toFile()
        assertTrue(calculatorClassFile.exists(), "Calculator.class not found at $calculatorClassPath")

        val calculatorBytes = calculatorClassFile.readBytes()
        val classFiles =
            mapOf(
                "com/github/rodrigotimoteo/mutation/sample/Calculator" to calculatorBytes,
            )

        // Run mutation testing
        val engine =
            MutationEngine(
                enabledOperators = MutationOperator.MVP_OPERATORS,
                timeoutMs = 10000,
                maxParallelMutants = 2,
            )

        val testClassNames =
            listOf(
                "com.github.rodrigotimoteo.mutation.sample.CalculatorTest",
            )

        val testClassBytes = loadTestClassFiles(testClassesDir)
        val report = engine.runMutationTesting(classFiles, testClassNames, testClassBytes)

        // Verify results
        assertTrue(report.totalMutations > 0, "Should generate at least one mutation")
        println("Generated ${report.totalMutations} mutations")
        println("Killed: ${report.killedMutations}")
        println("Survived: ${report.survivedMutations}")

        // Print detailed results
        for (result in report.results) {
            val mutation = result.mutation
            println(
                "  ${mutation.operator.operatorName}" +
                    " ${mutation.className}.${mutation.methodName}" +
                    ":${mutation.lineNumber} -> ${result.status}",
            )
        }

        // Our tests are comprehensive - should kill at least 50% of mutations
        val killRate =
            if (report.totalMutations > 0) {
                report.killedMutations.toDouble() / report.totalMutations
            } else {
                0.0
            }
        assertTrue(
            killRate >= 0.5,
            "Kill rate too low: ${report.killedMutations}/${report.totalMutations} = $killRate",
        )
    }

    @Test
    fun `engine should generate mutations with specific operators`() {
        val classesDir = findClassesDir()
        val calculatorClassPath = classesDir.toPath().resolve("com/github/rodrigotimoteo/mutation/sample/Calculator.class")
        val calculatorClassFile = calculatorClassPath.toFile()
        assertTrue(calculatorClassFile.exists(), "Calculator.class not found at $calculatorClassPath")

        val calculatorBytes = calculatorClassFile.readBytes()
        val classFiles =
            mapOf(
                "com/github/rodrigotimoteo/mutation/sample/Calculator" to calculatorBytes,
            )

        // Test with only arithmetic operators
        val engine =
            MutationEngine(
                enabledOperators = setOf(MutationOperator.ARITHMETIC),
                timeoutMs = 10000,
                maxParallelMutants = 2,
            )

        // Pass empty test list - this test only verifies mutation generation
        val report = engine.runMutationTesting(classFiles, emptyList(), emptyMap())

        // Should generate arithmetic mutations (+, -, *, /)
        assertTrue(report.totalMutations > 0, "Should generate arithmetic mutations")
        println("Arithmetic mutations generated: ${report.totalMutations}")
    }

    private fun loadTestClassFiles(dir: File): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        if (!dir.exists()) return result
        Files.walk(dir.toPath()).use { stream ->
            stream
                .filter { it.toString().endsWith(".class") }
                .forEach { path ->
                    val relativePath = dir.toPath().relativize(path)
                    val className =
                        relativePath.toString()
                            .replace(".class", "")
                            .replace("/", ".")
                            .replace("\\", ".")
                    result[className.replace('.', '/')] = path.toFile().readBytes()
                }
        }
        return result
    }

    private fun findClassesDir(): File {
        // Try different build output locations
        val candidates =
            listOf(
                File(buildDir, "classes/kotlin/main"),
                File(buildDir, "classes/java/main"),
                File(buildDir, "classes"),
            )
        return candidates.firstOrNull { it.exists() } ?: File(buildDir, "classes/kotlin/main")
    }

    private fun findTestClassesDir(): File {
        val candidates =
            listOf(
                File(buildDir, "classes/kotlin/test"),
                File(buildDir, "test-classes"),
                File(buildDir, "classes/java/test"),
            )
        return candidates.firstOrNull { it.exists() } ?: File(buildDir, "classes/kotlin/test")
    }
}
