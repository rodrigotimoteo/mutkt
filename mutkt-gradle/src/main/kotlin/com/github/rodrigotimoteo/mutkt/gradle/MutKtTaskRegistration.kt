package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import java.nio.file.Paths

/** Registers the MutKt tasks for a project with the java plugin, reading the JVM settings of its `test` task. */
internal fun Project.registerMutktTask(extension: MutKtExtension) {
    val model = checkNotNull(mutktModel()) { "The MutKt model exists once the java plugin is applied" }
    val test = providers.provider { tasks.named("test", Test::class.java).get() }
    registerMutkt(MutKtTask::class.java, extension, test) { task ->
        task.classesDirs.from(model.mainClasses)
        task.testClassesDirs.from(model.testClasses)
        task.projectSupportClassesDirs.from(model.supportClasses)
        task.testRuntimeClasspath.from(
            model.testRuntimeClasspath,
            missingPlatformLauncher(
                configurations.named(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME),
                extension.engines,
            ),
        )
        task.compileClasspath.from(model.mainCompileClasspath)
        task.sourceDirs.from(model.mainSources)
    }
}

/**
 * Registers `mutkt`, deliberately not attached to `check`, and `mutktUpdateBaseline`, which runs the same analysis and
 * writes its survivors to the project's [SURVIVOR_BASELINE], with the DSL values and the JVM settings of [test];
 * [inputs] sets the classes, classpaths and sources. [test] must not be a mapped task provider, which would make
 * them depend on (and run) the tests. Read lazily, later changes are seen and the tasks keep no project reference.
 */
internal fun <T : MutKtTask> Project.registerMutkt(
    type: Class<T>,
    extension: MutKtExtension,
    test: Provider<Test>,
    inputs: (T) -> Unit,
): List<TaskProvider<T>> {
    val baseline = layout.projectDirectory.file(SURVIVOR_BASELINE)
    val update =
        tasks.register("mutktUpdateBaseline", type) { task ->
            configureMutkt(task, extension, test, inputs)
            task.description = "Runs mutation testing and accepts every survivor in $SURVIVOR_BASELINE"
            task.failOnNewSurvivors.set(false)
            // Accepting the survivors is the point, so the score gate does not block the update.
            task.failBelowScore.set(null as Int?)
            task.updatedSurvivorBaseline.set(baseline)
        }
    val mutkt =
        tasks.register("mutkt", type) { task ->
            configureMutkt(task, extension, test, inputs)
            task.failOnNewSurvivors.set(extension.failOnNewSurvivors)
            // An input only when the gate reads it, so editing the file does not rerun an ungated task.
            task.survivorBaseline.set(extension.failOnNewSurvivors.map { on -> baseline.takeIf { on } })
            // Run together, the update writes the baseline this task reads.
            task.mustRunAfter(update)
        }
    return listOf(mutkt, update)
}

/** The committed survivor baseline, in the project directory. */
internal const val SURVIVOR_BASELINE: String = "mutkt-baseline.json"

private fun <T : MutKtTask> Project.configureMutkt(
    task: T,
    extension: MutKtExtension,
    test: Provider<Test>,
    inputs: (T) -> Unit,
) {
    task.group = "verification"
    task.description = "Runs mutation testing against the compiled classes"
    inputs(task)
    task.targetClasses.set(extension.targetClasses)
    task.excludedClasses.set(extension.excludedClasses)
    task.excludedMethods.set(extension.excludedMethods)
    task.threads.set(extension.threads)
    task.timeoutMs.set(extension.timeoutMs)
    task.maxMutantsPerClass.set(extension.maxMutantsPerClass)
    task.operators.set(extension.operators)
    task.engines.set(extension.engines)
    task.includeTags.set(extension.includeTags)
    task.excludeTags.set(extension.excludeTags)
    task.failBelowScore.set(extension.failBelowScore)
    task.consoleReport.set(extension.reports.console)
    task.readTestSettings(test)
    task.historyFile.set(extension.historyFile)
    task.generatedSourcesDir.set(layout.buildDirectory.dir("generated"))
    task.reportsDir.set(layout.buildDirectory.dir("reports/mutkt"))
    task.repositoryRoot.set(
        providers.environmentVariable("GITHUB_WORKSPACE").orElse(layout.projectDirectory.asFile.absolutePath),
    )
    task.githubAnnotations.set(providers.environmentVariable("GITHUB_ACTIONS").map { it == "true" }.orElse(false))
}

// The test task's JVM settings, read lazily.
private fun MutKtTask.readTestSettings(test: Provider<Test>) {
    // Coverage agents would record the mutant runs and rewrite their data files; other agents, such as a
    // mocking library's, stay because the tests may not run without them.
    testJvmArgs.set(test.map { it.allJvmArgs.filterNot(::isCoverageAgent) })
    testWorkingDir.set(test.map { it.workingDir.absolutePath })
    testJavaExecutable.set(
        test.map {
            it.javaLauncher.orNull
                ?.executablePath
                ?.asFile
                ?.absolutePath ?: currentJava()
        },
    )
    testJavaRuntime.set(
        test.map { tests ->
            tests.javaLauncher.orNull
                ?.metadata
                ?.let { "${it.vendor} ${it.javaRuntimeVersion}" } ?: currentJavaRuntime()
        },
    )
    testJavaVersion.set(
        test.map {
            it.javaLauncher.orNull
                ?.metadata
                ?.languageVersion
                ?.asInt() ?: Runtime.version().feature()
        },
    )
}

// JaCoCo's jacocoagent.jar, Kover's kover-jvm-agent and the IntelliJ coverage agent Kover used.
private val COVERAGE_AGENT = Regex("jacoco|kover|intellij-coverage", RegexOption.IGNORE_CASE)

// The jar path ends at the ".jar" that the options' '=' or the argument's end follows; a directory may hold '='.
private val AGENT_JAR = Regex("""^-javaagent:(.*?\.jar)(?:=|$)""", RegexOption.IGNORE_CASE)

private fun isCoverageAgent(arg: String): Boolean {
    val jar = AGENT_JAR.find(arg)?.groupValues?.get(1) ?: return false
    return COVERAGE_AGENT.containsMatchIn(jar.substringAfterLast('/').substringAfterLast('\\'))
}

private fun currentJavaRuntime(): String =
    "${System.getProperty("java.vendor")} ${System.getProperty("java.runtime.version")}"

private fun currentJava(): String = Paths.get(System.getProperty("java.home"), "bin", "java").toString()
