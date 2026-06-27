package samples.engine

import com.github.rodrigotimoteo.mutation.MutationId
import com.github.rodrigotimoteo.mutation.engine.MutationEngine
import com.github.rodrigotimoteo.mutation.engine.MutationEngineConfig
import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import java.io.File

/**
 * Programmatic-use examples for [MutationEngine].
 *
 * These functions are referenced by KDoc `@sample` tags on
 * [MutationEngine] and [MutationEngineConfig] — they are not called
 * by the engine itself. They exist to show how the public API is
 * expected to be consumed in real code, and to be linted by
 * `detekt`/`ktlint` like any other source file.
 *
 * Keep the examples minimal and self-contained: a reader of the
 * KDoc should be able to copy-paste one of these into a Gradle
 * task and have a working build.
 */
@Suppress("unused")
object MutationEngineSample {

    /**
     * Minimal run: defaults, all classes in the working dir, no
     * coverage, no incremental mode.
     */
    fun minimalRun(
        classFiles: Map<String, ByteArray>,
        testClassNames: List<String>,
    ): MutationReport {
        val engine = MutationEngine()
        return engine.runMutationTesting(classFiles, testClassNames)
    }

    /**
     * Recommended run: build a [MutationEngineConfig], tweak a few
     * fields, hand it to the engine. Groups related options so the
     * call site reads as named sections.
     */
    fun runWithConfig(
        classFiles: Map<String, ByteArray>,
        testClassNames: List<String>,
        projectDir: File,
        coverageExecFile: File,
    ): MutationReport {
        val config =
            MutationEngineConfig(
                enabledOperators = MutationOperator.values().toSet(),
                maxParallelMutants = 8,
                timeoutMs = 60_000L,
                enableSubsumption = true,
                enableWeakMutation = true,
                enableCache = true,
                includePatterns = listOf("com.example.service.*"),
                excludePatterns = listOf("com.example.legacy.*"),
                projectDir = projectDir,
                coverageExecFile = coverageExecFile,
            )
        val engine = MutationEngine(config)
        return engine.runMutationTesting(classFiles, testClassNames)
    }

    /**
     * Incremental run: limit mutation testing to a set of changed
     * classes (typically from `git diff`). Engine reuses cached
     * results for unchanged classes.
     */
    fun incrementalRun(
        classFiles: Map<String, ByteArray>,
        testClassNames: List<String>,
        changedClasses: Set<String>,
        projectDir: File,
    ): MutationReport {
        val engine =
            MutationEngine(
                config =
                    MutationEngineConfig(
                        changedClasses = changedClasses,
                        projectDir = projectDir,
                        enableCache = true,
                    ),
            )
        return engine.runMutationTesting(classFiles, testClassNames)
    }

    /**
     * Parse a mutation report and act on the score.
     */
    fun handleReport(report: MutationReport, minScore: Int = 80) {
        println("Killed ${report.killedMutations}/${report.totalMutations} (${report.killedPercentage}%)")
        if (report.killedPercentage < minScore) {
            error("Mutation score ${report.killedPercentage}% below threshold $minScore%")
        }
    }

    /**
     * Use a [MutationId] when filtering or reporting on individual
     * mutations. The engine builds the id from operator, class,
     * method, line, and occurrence index.
     */
    fun describeMutation(id: MutationId): String = "Mutation($id)"

    @Suppress("UNUSED_PARAMETER")
    private fun noop() {
        // Anchor for @sample references — see KDoc on MutationEngine
        // and MutationEngineConfig.
    }
}
