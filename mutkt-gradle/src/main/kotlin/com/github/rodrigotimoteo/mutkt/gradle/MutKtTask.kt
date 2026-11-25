package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Runs the mutation analysis in the Gradle daemon; the tests run in child JVMs (ADR 0008, no Worker API).
 * Everything the action reads is a declared input, so it never touches the project at execution time.
 */
@DisableCachingByDefault(because = "The result is a console report; tests run in forked JVMs")
public abstract class MutKtTask : DefaultTask() {
    /** Compiled main classes to mutate. */
    @get:Classpath
    public abstract val classesDirs: ConfigurableFileCollection

    /** Compiled test classes. */
    @get:Classpath
    public abstract val testClassesDirs: ConfigurableFileCollection

    /** The `test` task's classpath; its class directories are replaced by the two collections above. */
    @get:Classpath
    public abstract val testRuntimeClasspath: ConfigurableFileCollection

    /** Source roots, read for survivor snippets only. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val sourceDirs: ConfigurableFileCollection

    /** See [MutKtExtension.targetClasses]. */
    @get:Input
    public abstract val targetClasses: ListProperty<String>

    /** See [MutKtExtension.excludedClasses]. */
    @get:Input
    public abstract val excludedClasses: ListProperty<String>

    /** See [MutKtExtension.excludedMethods]. */
    @get:Input
    public abstract val excludedMethods: ListProperty<String>

    /** See [MutKtExtension.threads]. */
    @get:Input
    @get:Optional
    public abstract val threads: Property<Int>

    /** See [MutKtExtension.timeoutMs]. */
    @get:Input
    public abstract val timeoutMs: Property<Long>

    /** See [MutKtExtension.maxMutantsPerClass]. */
    @get:Input
    @get:Optional
    public abstract val maxMutantsPerClass: Property<Int>

    /** See [MutKtExtension.operators]. */
    @get:Input
    public abstract val operators: SetProperty<String>

    /** See [MutKtExtension.engines]. */
    @get:Input
    public abstract val engines: SetProperty<String>

    /** See [MutKtExtension.includeTags]. */
    @get:Input
    public abstract val includeTags: SetProperty<String>

    /** See [MutKtExtension.excludeTags]. */
    @get:Input
    public abstract val excludeTags: SetProperty<String>

    /** See [MutKtExtension.failBelowScore]. */
    @get:Input
    @get:Optional
    public abstract val failBelowScore: Property<Int>

    /** Whether the summary is logged. */
    @get:Input
    public abstract val consoleReport: Property<Boolean>

    /** JVM arguments of the project's `test` task, system properties included. */
    @get:Input
    public abstract val testJvmArgs: ListProperty<String>

    /** Working directory of the `test` task. */
    @get:Input
    public abstract val testWorkingDir: Property<String>

    /** The `java` executable of the `test` task's launcher. */
    @get:Input
    public abstract val testJavaExecutable: Property<String>

    /** Receives `mutkt.txt`, the console summary. */
    @get:OutputDirectory
    public abstract val reportsDir: DirectoryProperty

    /** Runs the analysis, writes and logs the summary, then applies the score threshold. */
    @TaskAction
    public fun analyse() {
        val result = runAnalysis()
        val (summary, score) = result
        val dir = reportsDir.get().asFile.also(File::mkdirs)
        val file = File(dir, "mutkt.txt")
        file.writeText(summary)
        if (consoleReport.get()) logger.lifecycle(summary.trimEnd())
        // Every error is a mutant the tests never ran against: not a clean build.
        if (result.mutants > 0 && result.errors == result.mutants) {
            throw GradleException("All ${result.mutants} mutants ended in ERROR: none loaded or ran, see the report")
        }
        if (score == null) {
            val note = if (failBelowScore.isPresent) "; failBelowScore is not applied" else ""
            logger.warn("No mutants were generated or scored, so there is no mutation score$note")
        }
        thresholdFailure(score, failBelowScore.orNull, file.path)?.let { throw GradleException(it) }
    }
}
