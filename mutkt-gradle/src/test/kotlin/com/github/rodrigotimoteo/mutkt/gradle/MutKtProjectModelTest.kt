package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import org.gradle.api.tasks.testing.Test as GradleTest

class MutKtProjectModelTest {
    private fun javaProject(
        parent: Project? = null,
        name: String = "p",
    ): Project =
        ProjectBuilder.builder().withName(name).apply { parent?.let { withParent(it) } }.build().also {
            it.pluginManager.apply("java")
            it.pluginManager.apply(MutKtPlugin::class.java)
        }

    private fun Project.sourceSet(name: String) =
        extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName(name)

    private fun Project.testTask() = tasks.getByName("test") as GradleTest

    private fun Project.names(files: Iterable<File>) = files.map { it.relativeTo(projectDir).path }.toSet()

    @Test
    fun `resolves directories and classpath from the java source sets`() {
        val project = javaProject()
        project.sourceSet("main").java.setSrcDirs(listOf("src/main/kotlin"))
        val model = requireNotNull(project.mutktModel())

        assertEquals(
            project
                .sourceSet("main")
                .output.classesDirs.files,
            model.mainClasses.files,
        )
        assertEquals(setOf("src/main/kotlin"), project.names(model.mainSources.files))
        assertEquals(
            project
                .sourceSet("test")
                .output.classesDirs.files,
            model.testClasses.files,
        )
        assertEquals(project.sourceSet("test").runtimeClasspath.files, model.testRuntimeClasspath.files)
        project.testTask().classpath += project.files("extra")
        assertTrue(File(project.projectDir, "extra") in model.testRuntimeClasspath.files)
        assertTrue(model.testRuntimeClasspath.files.containsAll(model.mainClasses.files))
    }

    @Test
    fun `is lazy about source set changes made after apply`() {
        val project = javaProject()
        val model = requireNotNull(project.mutktModel())
        project.sourceSet("main").java.srcDir("src/extra")

        assertTrue(project.names(model.mainSources.files).contains("src/extra"))
    }

    @Test
    fun `subprojects have independent models`() {
        val root = ProjectBuilder.builder().build()
        val a = javaProject(root, "a")
        val b = javaProject(root, "b")
        val modelA = requireNotNull(a.mutktModel())
        val modelB = requireNotNull(b.mutktModel())

        assertTrue(modelA.mainClasses.files.all { it.startsWith(a.projectDir) })
        assertTrue(modelB.mainClasses.files.all { it.startsWith(b.projectDir) })
        assertTrue(modelA.testRuntimeClasspath.files.none { it.startsWith(b.projectDir) })
    }

    @Test
    fun `java plugin applied after mutkt still yields a model`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(MutKtPlugin::class.java)
        assertNull(project.mutktModel())
        project.pluginManager.apply("java")

        requireNotNull(project.mutktModel())
    }

    @Test
    fun `without the java plugin there is no model`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(MutKtPlugin::class.java)

        assertNull(project.mutktModel())
    }
}
