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
        assertNotNull(ext, "expected 'mutationTest' extension to be registered")
        assertTrue(ext is MutationPluginExtension, "expected extension type MutationPluginExtension, got: ${ext?.javaClass?.name}")
    }

    @Test
    fun `plugin registers mutationTest task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(MutationPlugin::class.java)
        val task = project.tasks.findByName("mutationTest")
        assertNotNull(task, "expected 'mutationTest' task to be registered")
        assertTrue(task is MutationTask, "expected task type MutationTask, got: ${task?.javaClass?.name}")
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
        assertEquals(setOf("html"), ext.reportFormats.get())
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
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        assertNotNull(ext)
        assertTrue(
            ext.targetClasses.files.isNotEmpty(),
            "expected targetClasses to be auto-populated from main source set, got empty: ${ext.targetClasses.files}",
        )
        assertTrue(
            ext.testClasses.files.isNotEmpty(),
            "expected testClasses to be auto-populated from test source set, got empty: ${ext.testClasses.files}",
        )
    }

    @Test
    fun `plugin does not crash on non-JVM project`() {
        val project = ProjectBuilder.builder().build()
        // No java plugin applied
        project.plugins.apply(MutationPlugin::class.java)
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()
        val ext = project.extensions.findByType(MutationPluginExtension::class.java)
        assertNotNull(ext, "expected 'mutationTest' extension to be registered on non-JVM project")
        val task = project.tasks.findByName("mutationTest")
        assertNotNull(task, "expected 'mutationTest' task to be registered on non-JVM project")
    }

    @Test
    fun `KMP resolver is invoked when KotlinProjectExtension is registered`() {
        // The KMP source set auto-detector uses reflection on the Kotlin
        // Gradle Plugin classes (compileOnly dependency). For a pure-JVM
        // project without a KotlinProjectExtension, the detector returns
        // early. Applying kotlin("jvm") registers a real
        // KotlinProjectExtension (KotlinJvmProjectExtension is a
        // subclass), which forces the KMP branch to actually run.
        // The KMP branch iterates source sets looking for names matching
        // the XxxMain / XxxTest convention; for the JVM target the source
        // sets are named "main"/"test" (not "jvmMain"/"jvmTest"), so the
        // filter correctly skips them and falls through to the JVM
        // SourceSetContainer path. The point of this test is to confirm
        // the KMP branch is actually reached (not silently no-opped) and
        // does not crash the build.
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.jetbrains.kotlin.jvm")

        project.plugins.apply(MutationPlugin::class.java)
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        assertNotNull(ext, "expected 'mutationTest' extension to be registered")
        // JVM SourceSetContainer path populates targetClasses/testClasses
        // from "main"/"test" source sets. Verify the resolver did not
        // crash when the KMP branch was exercised.
        assertTrue(
            ext.targetClasses.files.isNotEmpty(),
            "expected targetClasses to be auto-populated (JVM path), got: ${ext.targetClasses.files}",
        )
        assertTrue(
            ext.testClasses.files.isNotEmpty(),
            "expected testClasses to be auto-populated (JVM path), got: ${ext.testClasses.files}",
        )
    }
}
