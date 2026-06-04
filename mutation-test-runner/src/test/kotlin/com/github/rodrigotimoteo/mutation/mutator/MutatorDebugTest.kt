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
        // Find the compiled class
        val buildDir = File("../mutation-sample/build/classes/kotlin/main")
        val classFile = buildDir.resolve("com/github/rodrigotimoteo/mutation/sample/Calculator.class")

        assertTrue(classFile.exists(), "Calculator.class not found at $classFile. Working dir: ${File(".").absolutePath}")

        val classBytes = classFile.readBytes()

        // Scan for mutations
        val mutator = Mutator(MutationOperator.MVP_OPERATORS)
        val foundMutations = mutator.scanMutations(classBytes)

        println("Found ${foundMutations.size} mutations:")
        for (mutation in foundMutations) {
            println("  ${mutation.operator.operatorName} ${mutation.methodName}:${mutation.lineNumber} - ${mutation.description}")
        }

        assertTrue(foundMutations.isNotEmpty(), "Should find at least one mutation")
    }
}
