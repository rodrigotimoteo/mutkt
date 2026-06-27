package com.github.rodrigotimoteo.mutation.engine

import com.github.rodrigotimoteo.mutation.ClassName
import com.github.rodrigotimoteo.mutation.ClassNamePattern
import com.github.rodrigotimoteo.mutation.DEFAULT_TIMEOUT_MS
import com.github.rodrigotimoteo.mutation.MutationId
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import java.io.File

/**
 * Grouped configuration for [MutationEngine].
 *
 * Bundles the 19+ constructor parameters of [MutationEngine] into a
 * single object so callers can:
 * - pass a config by reference (e.g. a test fixture reused across runs),
 * - document named groups in code (`Scan`, `Filter`, `Execution`, ...),
 * - share a config with multiple engines without copying every field.
 *
 * The original positional [MutationEngine] constructor is preserved
 * for backward compatibility; the new `MutationEngine(config)` overload
 * is the recommended entry point for new code.
 *
 * Defaults match the original constructor — `MutationEngine(MutationEngineConfig())`
 * is equivalent to `MutationEngine()` for every field.
 *
 * @property timeoutMs Timeout per mutant in milliseconds. Default: [DEFAULT_TIMEOUT_MS].
 * @property maxParallelMutants Parallel mutant executions. `0` means
 *   `Runtime.getRuntime().availableProcessors()`. Default: `4` (matches
 *   [com.github.rodrigotimoteo.mutation.runner.MutationTestRunnerFactory]).
 * @property enableInlinedFinally Skip mutations inside inlined `finally` blocks.
 * @property enableTestOrdering Order tests by historical strength.
 * @property changedClasses Limit mutation testing to these classes (incremental mode).
 * @property includePatterns Regex patterns for classes to include.
 * @property excludePatterns Regex patterns for classes to exclude.
 * @property targetTestPatterns Regex patterns for test classes to include.
 * @property excludeTestPatterns Regex patterns for test classes to exclude.
 * @property excludedMethods Method names (exact match) to skip during scanning.
 * @property maxMutationsPerClass Cap mutations per class (`0` = unlimited).
 * @property includeTags JUnit tags to include.
 * @property excludeTags JUnit tags to exclude.
 * @property engineIds JUnit Platform engine IDs to include when discovering tests.
 * @property enableCache Use file-based caching for results.
 * @property enableSubsumption Skip redundant mutations via subsumption analysis.
 * @property enableWeakMutation Skip unreachable mutations via coverage data.
 * @property projectDir Project root directory for caching/baseline.
 * @property coverageExecFile JaCoCo `.exec` file for coverage-guided filtering.
 */
@Suppress("LongParameterList", "ComplexMethod")
data class MutationEngineConfig(
    // --- Filter group ---
    val enabledOperators: Set<MutationOperator> = MutationOperator.MVP_OPERATORS,
    val excludedMethods: Set<String> = emptySet(),
    val maxMutationsPerClass: Int = 0,

    // --- Scan group ---
    val includePatterns: List<ClassNamePattern> = emptyList(),
    val excludePatterns: List<ClassNamePattern> = emptyList(),
    val targetTestPatterns: List<ClassNamePattern> = emptyList(),
    val excludeTestPatterns: List<ClassNamePattern> = emptyList(),

    // --- Test group ---
    val engineIds: List<String> =
        listOf(
            "junit-jupiter",
            "junit-vintage",
            "junit-platform-suite-engine",
        ),
    val includeTags: Set<String> = emptySet(),
    val excludeTags: Set<String> = emptySet(),

    // --- Execution group ---
    val maxParallelMutants: Int = 4,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val enableInlinedFinally: Boolean = true,
    val enableTestOrdering: Boolean = true,
    val enableCache: Boolean = false,
    val changedClasses: Set<ClassName> = emptySet(),

    // --- Analysis group ---
    val enableSubsumption: Boolean = true,
    val enableWeakMutation: Boolean = true,

    // --- Coverage ---
    val coverageExecFile: File? = null,

    // --- Filesystem ---
    val projectDir: File? = null,
) {
    companion object {
        /**
         * Convenience: build a config from a few explicit fields and
         * leave everything else at the default. Useful for callers that
         * only override a small subset and want the named-group ergonomics
         * without listing all 19 parameters.
         */
        @JvmStatic
        @JvmOverloads
        fun of(
            enabledOperators: Set<MutationOperator> = MutationOperator.MVP_OPERATORS,
            maxParallelMutants: Int = 4,
            timeoutMs: Long = DEFAULT_TIMEOUT_MS,
            projectDir: File? = null,
            coverageExecFile: File? = null,
        ): MutationEngineConfig =
            MutationEngineConfig(
                enabledOperators = enabledOperators,
                maxParallelMutants = maxParallelMutants,
                timeoutMs = timeoutMs,
                projectDir = projectDir,
                coverageExecFile = coverageExecFile,
            )

        /**
         * Empty config with every field at its default. Equivalent to
         * [MutationEngineConfig] called with no arguments.
         */
        @JvmStatic
        fun defaults(): MutationEngineConfig = MutationEngineConfig()
    }
}
