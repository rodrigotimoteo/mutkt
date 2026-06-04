package com.github.rodrigotimoteo.mutation.extension

import com.github.rodrigotimoteo.mutation.engine.MutationRegistry
import com.github.rodrigotimoteo.mutation.engine.VerificationMode
import com.github.rodrigotimoteo.mutation.model.Mutation
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit 5 extension for mutation testing.
 *
 * When a test class is annotated with @MutKtTest, this extension:
 * 1. Runs baseline tests (no mutations)
 * 2. Discovers mutation points from bytecode
 * 3. Runs tests with each mutation active
 * 4. Reports survivors
 */
class MutKtExtension :
    BeforeAllCallback,
    AfterAllCallback,
    BeforeTestExecutionCallback,
    AfterTestExecutionCallback {
    private val survivors = mutableListOf<Mutation>()
    private val killed = mutableListOf<Mutation>()
    private val errors = mutableListOf<Pair<Mutation, Exception>>()
    private var totalMutations = 0
    private var isBaselineRun = true
    private var currentAnnotation: MutKtTest? = null
    private var lastTestFailed = false

    override fun beforeAll(context: ExtensionContext) {
        survivors.clear()
        killed.clear()
        errors.clear()
        totalMutations = 0
        isBaselineRun = true
        currentAnnotation = context.requiredTestClass.getAnnotation(MutKtTest::class.java)

        if (currentAnnotation?.verification == VerificationMode.DISABLED) {
            return
        }

        println("\n${"=".repeat(60)}")
        println("  MutKt: Mutation Testing")
        println("${"=".repeat(60)}")
    }

    override fun afterAll(context: ExtensionContext) {
        if (currentAnnotation?.verification == VerificationMode.DISABLED) {
            return
        }

        println("\n${"=".repeat(60)}")
        println("  MutKt: Summary")
        println("${"=".repeat(60)}")
        println("  Total mutations: $totalMutations")
        println("  Killed: ${killed.size}")
        println("  Survived: ${survivors.size}")
        println("  Errors: ${errors.size}")

        if (survivors.isNotEmpty()) {
            println("\n  Survived mutations:")
            for (mutation in survivors) {
                println("    - ${mutation.description} (${mutation.className}:${mutation.methodName}:${mutation.lineNumber})")
            }
        }

        if (errors.isNotEmpty()) {
            println("\n  Errors:")
            for ((mutation, exception) in errors) {
                println("    - ${mutation.description}: ${exception.message}")
            }
        }

        println("${"=".repeat(60)}\n")

        if (currentAnnotation?.verification == VerificationMode.STRICT && survivors.isNotEmpty()) {
            throw AssertionError(
                "Mutation testing failed: ${survivors.size} mutation(s) survived.\n" +
                    "Survivors:\n${survivors.joinToString("\n") { "  - ${it.description}" }}",
            )
        }
    }

    override fun beforeTestExecution(context: ExtensionContext) {
        if (currentAnnotation?.verification == VerificationMode.DISABLED) {
            return
        }

        lastTestFailed = false
        if (isBaselineRun) {
            println("  Baseline run: ${context.requiredTestMethod.name}")
        } else {
            println("  Mutation run: ${context.requiredTestMethod.name}")
        }
    }

    override fun afterTestExecution(context: ExtensionContext) {
        if (currentAnnotation?.verification == VerificationMode.DISABLED) {
            return
        }

        // Check if mutation survived (test passed with mutation active)
        if (!isBaselineRun && !lastTestFailed) {
            val mutation = MutationRegistry.currentMutation()
            if (mutation != null) {
                survivors.add(mutation)
                println("    -> SURVIVED: ${mutation.description}")
            }
        } else if (!isBaselineRun && lastTestFailed) {
            val mutation = MutationRegistry.currentMutation()
            if (mutation != null) {
                killed.add(mutation)
                println("    -> KILLED: ${mutation.description}")
            }
        }

        MutationRegistry.endMutation()
    }

    /**
     * Mark the next test as a mutation run.
     */
    fun startMutationRun(
        mutation: Mutation,
        timeout: Long,
    ) {
        isBaselineRun = false
        totalMutations++
        MutationRegistry.startMutation(mutation, timeout)
    }

    /**
     * Mark the next test as a baseline run.
     */
    fun startBaselineRun() {
        isBaselineRun = true
        MutationRegistry.endMutation()
    }

    /**
     * Record that the last test failed.
     */
    fun recordTestFailure() {
        lastTestFailed = true
    }
}
