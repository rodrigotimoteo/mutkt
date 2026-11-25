package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class MutKtTaskTest {
    private fun project(vararg plugins: String): Project =
        ProjectBuilder.builder().build().also { project ->
            plugins.forEach(project.pluginManager::apply)
            project.pluginManager.apply(MutKtPlugin::class.java)
        }

    private fun Project.mutkt(): MutKtExtension = extensions.getByType(MutKtExtension::class.java)

    private fun Project.task(): MutKtTask = tasks.getByName("mutkt") as MutKtTask

    @Test
    fun `is registered in the verification group when the java plugin is present`() {
        val task = project("java").task()

        assertEquals("verification", task.group)
        assertNotNull(task.description)
    }

    @Test
    fun `is not registered without the java plugin`() {
        assertNull(project().tasks.findByName("mutkt"))
    }

    @Test
    fun `is registered when the java plugin comes after MutKt`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(MutKtPlugin::class.java)
        project.pluginManager.apply("java")

        assertNotNull(project.tasks.findByName("mutkt"))
    }

    @Test
    fun `is not attached to check`() {
        val project = project("java")
        val check = project.tasks.getByName("check")

        assertFalse(check.taskDependencies.getDependencies(check).any { it.name == "mutkt" })
    }

    @Test
    fun `declares class, source and classpath file inputs`() {
        assertEquals(setOf("classesDirs", "testClassesDirs", "testRuntimeClasspath"), annotated(Classpath::class.java))
        assertEquals(setOf("sourceDirs"), annotated(InputFiles::class.java))
        assertEquals(
            PathSensitivity.RELATIVE,
            MutKtTask::class.java
                .getMethod("getSourceDirs")
                .getAnnotation(PathSensitive::class.java)
                .value,
        )
    }

    private fun annotated(annotation: Class<out Annotation>): Set<String> =
        MutKtTask::class.java.methods
            .filter { it.isAnnotationPresent(annotation) }
            .map { it.name.removePrefix("get").replaceFirstChar(Char::lowercase) }
            .toSet()

    @Test
    fun `declares the extension and test task settings as value inputs`() {
        val task = project("java").task()

        assertEquals(
            setOf(
                "targetClasses",
                "excludedClasses",
                "excludedMethods",
                "operators",
                "engines",
                "includeTags",
                "excludeTags",
                "failBelowScore",
                "threads",
                "maxMutantsPerClass",
                "timeoutMs",
                "consoleReport",
                "testJvmArgs",
                "testWorkingDir",
                "testJavaExecutable",
            ),
            task.inputs.properties.keys,
        )
    }

    @Test
    fun `reports go to build reports mutkt`() {
        val project = project("java")

        assertEquals(
            listOf(
                File(
                    project.layout.buildDirectory
                        .get()
                        .asFile,
                    "reports/mutkt",
                ),
            ),
            project
                .task()
                .outputs.files.files
                .toList(),
        )
    }

    @Test
    fun `maps the extension values`() {
        val project = project("java")
        project.mutkt().apply {
            targetClasses.add("a.*")
            excludedClasses.add("a.Gen*")
            excludedMethods.add("toString")
            timeoutMs.set(500L)
            threads.set(3)
            maxMutantsPerClass.set(7)
            reports { it.console.set(false) }
        }
        val task = project.task()

        assertEquals(listOf("a.*"), task.targetClasses.get())
        assertEquals(listOf("a.Gen*"), task.excludedClasses.get())
        assertEquals(listOf("toString"), task.excludedMethods.get())
        assertEquals(500L, task.timeoutMs.get())
        assertEquals(3, task.threads.get())
        assertEquals(7, task.maxMutantsPerClass.get())
        assertFalse(task.consoleReport.get())
    }

    @Test
    fun `leaves threads and the mutant cap unset by default`() {
        val task = project("java").task()

        assertFalse(task.threads.isPresent)
        assertFalse(task.maxMutantsPerClass.isPresent)
        assertTrue(task.consoleReport.get())
    }

    @Test
    fun `captures the test task's jvm arguments, working directory and launcher`() {
        val project = project("java")
        val dir =
            project.layout.projectDirectory
                .dir("work")
                .asFile
        project.tasks.named("test", org.gradle.api.tasks.testing.Test::class.java).configure {
            it.jvmArgs("-Xmx64m")
            it.systemProperty("mutkt.sample", "yes")
            it.jvmArgs("-javaagent:/agents/cover.jar=out", "-javaagent:/agents/other.jar")
            it.workingDir(dir)
        }
        val task = project.task()

        assertTrue("-Xmx64m" in task.testJvmArgs.get())
        assertTrue("-Dmutkt.sample=yes" in task.testJvmArgs.get())
        assertTrue(task.testJvmArgs.get().none { it.startsWith("-javaagent") }, "agents are not forwarded")
        assertEquals(dir.absolutePath, task.testWorkingDir.get())
        assertTrue(File(task.testJavaExecutable.get()).isFile)
    }

    @Test
    fun `does not depend on the test task`() {
        val project = project("java")
        val task = project.task()

        assertFalse(task.taskDependencies.getDependencies(task).any { it.name == "test" })
    }

    @Test
    fun `rejects each setting the adapters cannot honour yet`() {
        val cases =
            mapOf<String, (MutKtExtension) -> Unit>(
                "operators" to { it.operators.add("arithmetic") },
                "engines" to { it.engines.add("vintage") },
                "includeTags" to { it.includeTags.add("fast") },
                "excludeTags" to { it.excludeTags.add("slow") },
                "failBelowScore (ROD-107)" to { it.failBelowScore.set(80) },
            )
        cases.forEach { (name, configure) ->
            val project = project("java")
            configure(project.mutkt())

            val failure = assertThrows<GradleException> { project.task().runAnalysis() }

            assertEquals("$name is not supported yet", failure.message)
        }
    }

    @Test
    fun `classes are wired from the main and test source sets`() {
        val project = project("java")
        val task = project.task()
        val model = checkNotNull(project.mutktModel())

        assertEquals(model.mainClasses.files, task.classesDirs.files)
        assertEquals(model.testClasses.files, task.testClassesDirs.files)
        assertTrue(task.classesDirs.files.isNotEmpty())
    }
}
