package com.github.rodrigotimoteo.mutation.gradle

import com.github.rodrigotimoteo.mutation.DEFAULT_TIMEOUT_MS
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/**
 * DSL extension for configuring mutation testing.
 *
 * Provides a declarative API for configuring mutation testing behavior,
 * including operators, timeouts, reporting, and filtering.
 *
 * Example:
 * ```kotlin
 * mutationTest {
 *     // Core settings
 *     timeoutMs.set(60000)
 *     maxParallelMutants.set(8)
 *
 *     // Reporting
 *     reportFormats.set(setOf("html", "console"))
 *     failOnScoreThreshold.set(70)
 *
 *     // Filtering
 *     targetClassPatterns.set(listOf("com\\.example\\..*"))
 *     excludeClassPatterns.set(listOf("com\\.example\\.generated\\..*"))
 *
 *     // Speed optimizations
 *     enableSubsumption.set(true)
 *     enableWeakMutation.set(true)
 * }
 * ```
 *
 * @property targetClasses Target classes to mutate (auto-detected from sourceSets)
 * @property testClasses Test classes to run (auto-detected from sourceSets)
 * @property classpath Classpath for test execution (auto-detected)
 * @property enabledOperators Mutation operators to apply
 * @property timeoutMs Timeout per mutant in milliseconds
 * @property maxParallelMutants Number of parallel mutant executions
 * @property reportFormats Report formats to generate
 * @property failOnScoreThreshold Fail build if score below this (0-100)
 */
open class MutationPluginExtension(project: Project) {
    /**
     * Target classes to mutate. Auto-detected from sourceSets.main.output.
     */
    val targetClasses: ConfigurableFileCollection = project.objects.fileCollection()

    /**
     * Test classes to run against mutations. Auto-detected from sourceSets.test.output.
     */
    val testClasses: ConfigurableFileCollection = project.objects.fileCollection()

    /**
     * Classpath for test execution. Auto-detected from testRuntimeClasspath.
     */
    val classpath: ConfigurableFileCollection = project.objects.fileCollection()

    /**
     * JaCoCo execution file for coverage analysis. Auto-detected if JaCoCo plugin is applied.
     */
    val coverageExecFile: RegularFileProperty = project.objects.fileProperty()

    /**
     * List of mutation operators to enable (use MVP_OPERATORS by default).
     */

    val enabledOperators: SetProperty<String> =
        project.objects.setProperty(String::class.java).convention(
            setOf(
                "CONDITIONALS_BOUNDARY",
                "NEGATE_CONDITIONALS",
                "ARITHMETIC",
                "RETURN_VALS",
                "NULL_RETURNS",
                "EMPTY_RETURNS",
            ),
        )

    /**
     * Timeout in milliseconds for each mutant test execution.
     */

    val timeoutMs: Property<Long> = project.objects.property(Long::class.java).convention(DEFAULT_TIMEOUT_MS)

    /**
     * Number of parallel mutant test executions.
     */

    val maxParallelMutants: Property<Int> =
        project.objects.property(Int::class.java)
            .convention(4)

    /**
     * Output directory for mutation test reports.
     */

    val outputDir: DirectoryProperty =
        project.objects.directoryProperty()
            .convention(project.layout.buildDirectory.dir("reports/mutation"))

    /**
     * Whether to fail the build if any mutants survive.
     */

    val failOnSurvived: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

    /**
     * Classes to exclude from mutation testing.
     * Patterns: ** for any path, * for any name, exact names.
     */

    val excludedClasses: SetProperty<String> =
        project.objects.setProperty(String::class.java).convention(
            setOf(
                "**/*Test",
                "**/*Tests",
                "**/*_",
                "**/BuildConfig",
                "**/R.class",
                "**/databinding/**",
                "**/BR.class",
                "**/*_Factory",
                "**/*_MembersInjector",
                "**/*Module_*",
                "**/*_Impl",
                "**/META-INF/**",
                "**/*.kotlin_module",
                "**/*\$Lambda\$*",
                "**/*\$inlined\$*",
            ),
        )

    /**
     * Methods to exclude from mutation testing.
     */

    val excludedMethods: SetProperty<String> =
        project.objects.setProperty(String::class.java).convention(
            setOf(
                "main",
                "toString",
                "hashCode",
                "equals",
                "clone",
                "finalize",
            ),
        )

    /**
     * Whether to run JaCoCo agent automatically for coverage analysis.
     *
     * Reserved for future use; not yet consumed by the engine. Kept on the
     * extension so user scripts referencing it keep compiling.
     */
    @Deprecated("Not yet implemented; reserved for future JaCoCo agent auto-run")
    val autoRunJaCoCo: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    /**
     * Mutation score threshold (0-100). Fail build if score below this.
     *
     * @deprecated Use [failOnMutationScoreThreshold] instead. The score
     *   this property compared was always the mutation score (not line
     *   coverage), so the legacy name was misleading.
     */
    @Deprecated(
        "Use failOnMutationScoreThreshold instead",
        ReplaceWith("failOnMutationScoreThreshold"),
    )
    val failOnScoreThreshold: Property<Int> = project.objects.property(Int::class.java).convention(0)

    /**
     * Mutation score threshold (0-100). Fail build if mutation score below this.
     * Note: previously named `failOnCoverageThreshold` — the legacy name
     * compared the mutation score (not line coverage) and was misleading.
     */

    val failOnMutationScoreThreshold: Property<Int> = project.objects.property(Int::class.java).convention(0)

    /**
     * Maximum mutations per class. 0 = no limit.
     */

    val maxMutationsPerClass: Property<Int> = project.objects.property(Int::class.java).convention(0)

    /**
     * Enable incremental analysis (cache mutation results).
     */

    val enableIncrementalAnalysis: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    /**
     * Timeout per mutant in milliseconds.
     *
     * @deprecated Use [timeoutMs] instead. The two properties duplicate
     *   the same setting with confusing precedence; [timeoutMs] is the
     *   single source of truth.
     */
    @Deprecated("Use timeoutMs instead", ReplaceWith("timeoutMs"))
    val mutantTimeoutMs: Property<Long> = project.objects.property(Long::class.java).convention(0)

    // === Regex Filtering ===

    /**
     * Target classes to mutate (regex patterns).
     * If empty, all classes are targeted.
     * Example: listOf("com\\.example\\..*")
     */

    val targetClassPatterns: SetProperty<String> = project.objects.setProperty(String::class.java).convention(emptySet())

    /**
     * Target tests to run (regex patterns).
     * If empty, all tests are run.
     * Example: listOf("com\\.example\\..*Test")
     */

    val targetTestPatterns: SetProperty<String> = project.objects.setProperty(String::class.java).convention(emptySet())

    /**
     * Exclude classes matching these patterns.
     * Example: listOf("com\\.example\\.generated\\..*")
     */

    val excludeClassPatterns: SetProperty<String> = project.objects.setProperty(String::class.java).convention(emptySet())

    /**
     * Exclude tests matching these patterns.
     * Example: listOf("com\\.example\\.Generated.*")
     */

    val excludeTestPatterns: SetProperty<String> = project.objects.setProperty(String::class.java).convention(emptySet())

    // === Speed Optimizations ===

    /**
     * Enable subsumption detection (skip redundant mutants).
     */

    val enableSubsumption: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    /**
     * Enable weak mutant detection (skip mutations never reached).
     */

    val enableWeakMutation: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    /**
     * Enable inlined finally detection (skip inlined finally blocks).
     */

    val enableInlinedFinally: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    /**
     * Enable test ordering (run most effective tests first).
     */

    val enableTestOrdering: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    // === Report Options ===

    /**
     * Report formats to generate: "html", "xml", "json", "csv", "console", "graph"
     */

    val reportFormats: SetProperty<String> =
        project.objects.setProperty(String::class.java).convention(
            setOf("html"),
        )

    /**
     * Generate per-class mutation scores in reports.
     */

    val showClassScores: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    /**
     * Generate interactive HTML graph of test-mutant relationships.
     *
     * @deprecated Use [reportFormats] instead. The graph report is one
     *   of the available report formats; this boolean duplicates that
     *   control and creates two ways to enable the same output.
     */
    @Deprecated(
        "Use reportFormats instead",
        ReplaceWith("reportFormats.add(\"graph\")"),
    )
    val generateGraph: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

    // === Caching ===

    /**
     * Enable file-based caching of mutation results.
     * Skips previously tested mutations on re-run.
     */

    val enableCache: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

    // === Power Features ===

    /**
     * Target specific packages for mutation testing.
     * If empty, all classes in targetClasses are mutated.
     * Example: setOf("com.example.service", "com.example.model")
     */
    val targetPackages: SetProperty<String> = project.objects.setProperty(String::class.java).convention(emptySet())

    /**
     * Exclude specific packages from mutation testing.
     * Example: setOf("com.example.generated", "com.example.test")
     */
    val excludePackages: SetProperty<String> = project.objects.setProperty(String::class.java).convention(emptySet())

    /**
     * CI mode: generate machine-readable reports and exit with proper codes.
     * When true: enables console report, disables interactive features, uses exit codes.
     */
    val ciMode: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

    /**
     * Verbose output: show all mutations tested, not just summary.
     *
     * Reserved for future use; not yet wired to engine logging level.
     */
    @Deprecated("Not yet implemented; reserved for future verbose logging mode")
    val verbose: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

    // === Android Support ===

    /**
     * True when an Android plugin (com.android.application, com.android.library,
     * com.android.test, etc.) is applied to the project. Set automatically by
     * the plugin when AGP is detected; can be set manually for advanced cases.
     */
    val isAndroid: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

    /**
     * Type of the Android plugin applied: "application", "library", or
     * "test". Empty string when no Android plugin is detected. Set
     * automatically by the plugin when AGP is detected.
     */
    val androidPluginType: Property<String> = project.objects.property(String::class.java).convention("")

    /**
     * Android build variant to target (e.g. "debug", "release"). Defaults
     * to "debug" because unit tests are typically run against the debug
     * variant in CI.
     */
    val androidVariant: Property<String> = project.objects.property(String::class.java).convention("debug")

    /**
     * Generated classes to exclude from mutation testing. Defaults cover
     * the most common Android/Kotlin codegen outputs: R, R$*, BuildConfig,
     * ComposableSingletons$*, databinding, Dagger/Hilt factories, and
     * Kotlin synthetic helpers.
     */
    val excludeGeneratedClasses: SetProperty<String> =
        project.objects.setProperty(String::class.java).convention(
            setOf(
                "**/R",
                "**/R$*",
                "**/BuildConfig",
                "**/ComposableSingletons$*",
                "**/databinding/**",
                "**/BR",
                "**/*_Factory",
                "**/*_Factory$*",
                "**/*_MembersInjector",
                "**/*_HiltModules$*",
                "**/*_HiltModules",
                "**/*_GeneratedInjector",
                "**/Hilt_*",
                "**/*Module_*",
                "**/*_Impl",
                "**/*_Provide*Factory",
                "**/Dagger*Component*",
                "**/*\$Lambda\$*",
                "**/*\$inlined\$*",
                "**/META-INF/**",
            ),
        )

    /**
     * Resolved Android variant context, set by the plugin after project
     * evaluation when an Android plugin is detected. Consumed by
     * [MutationTask] to wire variant-specific classpath, classes dirs,
     * compile tasks, and the `android.jar` location.
     */
    val androidContext: Property<AndroidMutationContext> =
        project.objects.property(AndroidMutationContext::class.java)
}
