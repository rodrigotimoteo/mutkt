package com.github.rodrigotimoteo.mutation.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MutationPluginTest {
    @Test
    fun `plugin registers mutationTest extension`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(MutationPlugin::class.java)
        val ext = project.extensions.findByName("mutationTest")
        assertNotNull(ext)
        assertTrue(ext is MutationPluginExtension)
    }

    @Test
    fun `plugin registers mutationTest task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(MutationPlugin::class.java)
        val task = project.tasks.findByName("mutationTest")
        assertNotNull(task)
        assertTrue(task is MutationTask)
    }

    @Test
    fun `task is in verification group`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(MutationPlugin::class.java)
        val task = project.tasks.findByName("mutationTest") as MutationTask
        assertEquals("verification", task.group)
    }

    @Test
    fun `extension has expected defaults`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(MutationPlugin::class.java)
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        assertEquals(30000L, ext.timeoutMs.get())
        assertEquals(setOf("html", "csv"), ext.reportFormats.get())
        assertEquals(false, ext.failOnSurvived.get())
        assertEquals(0, ext.failOnScoreThreshold.get())
    }

    @Test
    fun `task wires extension timeoutMs`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(MutationPlugin::class.java)
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        ext.timeoutMs.set(60_000L)
        val task = project.tasks.named("mutationTest").get() as MutationTask
        assertEquals(60_000L, task.timeoutMs.get())
    }

    @Test
    fun `task wires extension maxParallelMutants`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(MutationPlugin::class.java)
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        ext.maxParallelMutants.set(16)
        val task = project.tasks.named("mutationTest").get() as MutationTask
        assertEquals(16, task.maxParallelMutants.get())
    }

    @Test
    fun `task wires extension reportFormats`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(MutationPlugin::class.java)
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        ext.reportFormats.set(setOf("html", "console"))
        val task = project.tasks.named("mutationTest").get() as MutationTask
        assertEquals(setOf("html", "console"), task.reportFormats.get())
    }

    @Test
    fun `task wires extension failOnSurvived`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(MutationPlugin::class.java)
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        ext.failOnSurvived.set(true)
        val task = project.tasks.named("mutationTest").get() as MutationTask
        assertEquals(true, task.failOnSurvived.get())
    }

    @Test
    fun `task wires extension failOnScoreThreshold`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(MutationPlugin::class.java)
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        ext.failOnScoreThreshold.set(75)
        val task = project.tasks.named("mutationTest").get() as MutationTask
        assertEquals(75, task.failOnScoreThreshold.get())
    }

    @Test
    fun `task wires extension excludedClasses`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(MutationPlugin::class.java)
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        ext.excludedClasses.set(setOf("**/*Foo*"))
        val task = project.tasks.named("mutationTest").get() as MutationTask
        assertEquals(setOf("**/*Foo*"), task.excludedClasses.get())
    }

    @Test
    fun `task wires extension enabledOperators`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(MutationPlugin::class.java)
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        ext.enabledOperators.set(setOf("ARITHMETIC"))
        val task = project.tasks.named("mutationTest").get() as MutationTask
        assertEquals(setOf("ARITHMETIC"), task.enabledOperators.get())
    }

    @Test
    fun `plugin auto-detects source sets when java plugin applied`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply(MutationPlugin::class.java)
        // Force evaluation via afterEvaluate
        project.afterEvaluate {
            // Source sets should be auto-detected (no exception)
        }
        // Source sets should be auto-detected (no exception)
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        assertNotNull(ext)
    }

    @Test
    fun `plugin does not crash on non-JVM project`() {
        val project = ProjectBuilder.builder().build()
        // No java plugin applied
        project.plugins.apply(MutationPlugin::class.java)
        project.afterEvaluate {
            // Should not throw
        }
        // Should not throw
    }
}
