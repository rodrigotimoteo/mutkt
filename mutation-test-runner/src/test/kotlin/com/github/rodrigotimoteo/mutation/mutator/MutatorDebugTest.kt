package com.github.rodrigotimoteo.mutation.mutator

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Debug test to verify mutation scanning works.
 */
class MutatorDebugTest {
    @Test
    fun `scanner should find mutations in Calculator class`() {
        val classBytes = loadCalculatorClassBytes()

        // Scan for mutations
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val foundMutations = mutator.scanMutations(classBytes)

        assertTrue(foundMutations.isNotEmpty(), "Should find at least one mutation")
        assertTrue(foundMutations.size >= 5, "Should find multiple mutations, found: ${foundMutations.size}")
    }

    /**
     * Locate Calculator.class by trying multiple build output paths.
     * Works from both Gradle module dir and IDE project root.
     */
    private fun loadCalculatorClassBytes(): ByteArray {
        val cwd = File(System.getProperty("user.dir"))
        // Walk up to find mutation-sample module if not already there
        val moduleRoot =
            generateSequence(cwd) { it.parentFile }
                .firstOrNull { File(it, "mutation-sample/build/classes/kotlin/main").exists() }
                ?: cwd

        val candidates =
            listOf(
                File(moduleRoot, "mutation-sample/build/classes/kotlin/main"),
                File(moduleRoot, "mutation-sample/build/classes/java/main"),
                File(moduleRoot, "mutation-sample/build/classes"),
            )

        val classesDir =
            candidates.firstOrNull { it.exists() }
                ?: error(
                    "Calculator classes not found. Tried: ${candidates.joinToString { it.path }}\n" +
                        "Working dir: ${cwd.absolutePath}\n" +
                        "Run ':mutation-sample:compileKotlin' first.",
                )

        val classFile = classesDir.resolve("com/github/rodrigotimoteo/mutation/sample/Calculator.class")
        require(classFile.exists()) {
            "Calculator.class not found at $classFile. Run ':mutation-sample:compileKotlin' first."
        }
        return classFile.readBytes()
    }
}
