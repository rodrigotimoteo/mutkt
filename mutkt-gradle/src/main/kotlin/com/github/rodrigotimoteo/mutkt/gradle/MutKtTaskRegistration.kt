package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import java.nio.file.Paths

/**
 * Registers `mutkt`, deliberately not attached to `check`. The `test` task's JVM settings are read lazily through
 * providers, so later changes to it are seen and the task itself keeps no project reference.
 */
internal fun Project.registerMutktTask(extension: MutKtExtension) {
    val model = checkNotNull(mutktModel()) { "The MutKt model exists once the java plugin is applied" }
    val test = tasks.named("test", Test::class.java)

    // Not test.map { }: a mapped task provider would make mutkt depend on (and run) the test task.
    fun <T : Any> fromTest(read: (Test) -> T) = providers.provider { read(test.get()) }
    tasks.register("mutkt", MutKtTask::class.java) { task ->
        task.group = "verification"
        task.description = "Runs mutation testing against the compiled classes"
        task.classesDirs.from(model.mainClasses)
        task.testClassesDirs.from(model.testClasses)
        task.projectSupportClassesDirs.from(model.supportClasses)
        task.testRuntimeClasspath.from(model.testRuntimeClasspath)
        task.sourceDirs.from(model.mainSources)
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
        // Coverage agents would record the mutant runs and rewrite their data files; other agents, such as a
        // mocking library's, stay because the tests may not run without them.
        task.testJvmArgs.set(fromTest { it.allJvmArgs.filterNot(::isCoverageAgent) })
        task.testWorkingDir.set(fromTest { it.workingDir.absolutePath })
        task.testJavaExecutable.set(
            fromTest {
                it.javaLauncher.orNull
                    ?.executablePath
                    ?.asFile
                    ?.absolutePath ?: currentJava()
            },
        )
        task.reportsDir.set(layout.buildDirectory.dir("reports/mutkt"))
    }
}

// JaCoCo's jacocoagent.jar, Kover's kover-jvm-agent and the IntelliJ coverage agent Kover used.
private val COVERAGE_AGENT = Regex("jacoco|kover|intellij-coverage", RegexOption.IGNORE_CASE)

// The jar path ends at the ".jar" that the options' '=' or the argument's end follows; a directory may hold '='.
private val AGENT_JAR = Regex("""^-javaagent:(.*?\.jar)(?:=|$)""", RegexOption.IGNORE_CASE)

private fun isCoverageAgent(arg: String): Boolean {
    val jar = AGENT_JAR.find(arg)?.groupValues?.get(1) ?: return false
    return COVERAGE_AGENT.containsMatchIn(jar.substringAfterLast('/').substringAfterLast('\\'))
}

private fun currentJava(): String = Paths.get(System.getProperty("java.home"), "bin", "java").toString()
