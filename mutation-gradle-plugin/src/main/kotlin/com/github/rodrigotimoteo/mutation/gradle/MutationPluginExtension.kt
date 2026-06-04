package com.github.rodrigotimoteo.mutation.gradle

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * DSL extension for configuring mutation testing.
 *
 * Usage:
 * ```kotlin
 * mutationTest {
 *     // Auto-detected from sourceSets, override if needed
 *     timeoutMs.set(60000)
 *     maxParallelMutants.set(8)
 *     excludedClasses.addAll("com/example/Generated*", "com/example/BuildConfig")
 * }
 * ```
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
    @Input
    @Optional
    val enabledOperators: SetProperty<String> = project.objects.setProperty(String::class.java).convention(
        setOf(
            "CONDITIONALS_BOUNDARY",
            "NEGATE_CONDITIONALS",
            "ARITHMETIC",
            "RETURN_VALS",
            "NULL_RETURNS",
            "EMPTY_RETURNS",
            "INVERT_NEGS"
        )
    )

    /**
     * Timeout in milliseconds for each mutant test execution.
     */
    @Input
    @Optional
    val timeoutMs: Property<Long> = project.objects.property(Long::class.java).convention(30000)

    /**
     * Number of parallel mutant test executions.
     */
    @Input
    @Optional
    val maxParallelMutants: Property<Int> = project.objects.property(Int::class.java)
        .convention(Runtime.getRuntime().availableProcessors())

    /**
     * Report format: "html", "xml", "json", "console"
     */
    @Input
    @Optional
    val reportFormat: Property<String> = project.objects.property(String::class.java).convention("html")

    /**
     * Output directory for mutation test reports.
     */
    @Input
    @Optional
    val outputDir: Property<String> = project.objects.property(String::class.java)
        .convention("build/reports/mutation")

    /**
     * Whether to fail the build if any mutants survive.
     */
    @Input
    @Optional
    val failOnSurvived: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

    /**
     * Classes to exclude from mutation testing.
     * Patterns: ** for any path, * for any name, exact names.
     */
    @Input
    @Optional
    val excludedClasses: SetProperty<String> = project.objects.setProperty(String::class.java).convention(
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
            "**/*\$inlined\$*"
        )
    )

    /**
     * Methods to exclude from mutation testing.
     */
    @Input
    @Optional
    val excludedMethods: SetProperty<String> = project.objects.setProperty(String::class.java).convention(
        setOf(
            "main",
            "toString",
            "hashCode",
            "equals",
            "clone",
            "finalize"
        )
    )

    /**
     * Whether to run JaCoCo agent automatically for coverage analysis.
     */
    @Input
    @Optional
    val autoRunJaCoCo: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    /**
     * Mutation score threshold (0-100). Fail build if score below this.
     */
    @Input
    @Optional
    val failOnScoreThreshold: Property<Int> = project.objects.property(Int::class.java).convention(0)

    /**
     * Line coverage threshold (0-100). Fail build if coverage below this.
     */
    @Input
    @Optional
    val failOnCoverageThreshold: Property<Int> = project.objects.property(Int::class.java).convention(0)

    /**
     * Maximum mutations per class. 0 = no limit.
     */
    @Input
    @Optional
    val maxMutationsPerClass: Property<Int> = project.objects.property(Int::class.java).convention(0)

    /**
     * Enable incremental analysis (cache mutation results).
     */
    @Input
    @Optional
    val enableIncrementalAnalysis: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)

    /**
     * Timeout per mutant in milliseconds.
     */
    @Input
    @Optional
    val mutantTimeoutMs: Property<Long> = project.objects.property(Long::class.java).convention(10000)
}