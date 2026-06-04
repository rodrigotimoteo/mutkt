package com.github.rodrigotimoteo.mutation.gradle

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import java.io.File

/**
 * DSL extension for configuring mutation testing.
 */
open class MutationPluginExtension {
    /**
     * List of mutation operators to enable (use MVP_OPERATORS by default).
     */
    @Input
    @Optional
    var enabledOperators: List<String> = listOf(
        "CONDITIONALS_BOUNDARY",
        "NEGATE_CONDITIONALS",
        "ARITHMETIC",
        "RETURN_VALS",
        "NULL_RETURNS",
        "EMPTY_RETURNS",
        "INVERT_NEGS"
    )

    /**
     * Timeout in milliseconds for each mutant test execution.
     */
    @Input
    @Optional
    var timeoutMs: Long = 30000

    /**
     * Number of parallel mutant test executions.
     */
    @Input
    @Optional
    var maxParallelMutants: Int = 4

    /**
     * Report format: "html", "xml", "json", "console"
     */
    @Input
    @Optional
    var reportFormat: String = "console"

    /**
     * Output directory for mutation test reports.
     */
    @Input
    @Optional
    var outputDir: File = File("build/reports/mutation-test")

    /**
     * Whether to fail the build if any mutants survive.
     */
    @Input
    @Optional
    var failOnSurvived: Boolean = false
}