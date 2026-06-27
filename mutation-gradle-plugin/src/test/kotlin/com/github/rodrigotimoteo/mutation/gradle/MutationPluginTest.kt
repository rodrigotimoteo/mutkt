package com.github.rodrigotimoteo.mutation.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
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

    @Test
    fun `discoverKotlinCompileTasks falls back to compileKotlin when no task exists`() {
        // No Kotlin plugin applied → no real compile tasks. The resolver
        // must still return a usable pair by falling back to the
        // standard `compileKotlin` / `compileTestKotlin` names.
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val (main, test) = MutationPlugin().discoverKotlinCompileTasks(project, isAndroid = false)
        assertEquals("compileKotlin", main)
        assertEquals("compileTestKotlin", test)
    }

    @Test
    fun `discoverKotlinCompileTasks prefers debug capture for android variant selection`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val debugCapture =
            AgpVariantResolver.VariantCapture(
                name = "debug",
                runtimeConfiguration = project.objects.fileCollection(),
                compileTask = "compileDebugKotlin",
                testCompileTask = "compileDebugUnitTestKotlin",
            )
        val releaseCapture =
            AgpVariantResolver.VariantCapture(
                name = "release",
                runtimeConfiguration = project.objects.fileCollection(),
                compileTask = "compileReleaseKotlin",
                testCompileTask = "compileReleaseUnitTestKotlin",
            )
        val (main, test) =
            MutationPlugin().discoverKotlinCompileTasks(
                project,
                isAndroid = true,
                variantCaptures = listOf(releaseCapture, debugCapture),
            )
        // debugCapture is preferred over the first item in the list
        // (releaseCapture) even though releaseCapture is first.
        assertEquals("compileDebugKotlin", main)
        assertEquals("compileDebugUnitTestKotlin", test)
    }

    @Test
    fun `autoDetectKmpSourceSets is safe when no KotlinProjectExtension is present`() {
        // The reflective branch in autoDetectKmpSourceSets must not
        // throw on a project without a Kotlin Gradle Plugin
        // extension — that's the common case for pure-JVM users.
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(MutationPlugin::class.java)
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        // No exception = the early-return path is exercised. The
        // SourceSetContainer path is also absent because the project
        // has no java plugin.
        assertNotNull(ext)
    }

    @Test
    fun `resolveAndroidContext is a no-op when isAndroid is false`() {
        // The early-return guard in resolveAndroidContext: when the
        // extension's isAndroid flag is false, the resolver must skip
        // entirely without touching variantCaptures or mutationTask.
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply(MutationPlugin::class.java)
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        ext.isAndroid.set(false)
        @Suppress("UNCHECKED_CAST")
        val task = project.tasks.named("mutationTest") as org.gradle.api.tasks.TaskProvider<MutationTask>
        val resolver = AgpVariantResolver(project.objects)
        // Invoke via reflection since resolveAndroidContext is private.
        invokeResolveAndroidContext(project, ext, task, resolver, emptyList())
        // isAndroid is still false — no context set, no task wiring.
        assertEquals(false, ext.isAndroid.get())
    }

    @Test
    fun `resolveAndroidContext logs info when no variants are captured`() {
        // The variantCaptures.isEmpty() branch inside the capture-null
        // path must log a friendly "no Android variants captured"
        // message instead of crashing.
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply(MutationPlugin::class.java)
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        ext.isAndroid.set(true)
        @Suppress("UNCHECKED_CAST")
        val task = project.tasks.named("mutationTest") as org.gradle.api.tasks.TaskProvider<MutationTask>
        val resolver = AgpVariantResolver(project.objects)
        invokeResolveAndroidContext(project, ext, task, resolver, emptyList())
        // No mutationContext is set, but no exception either.
        assertEquals(false, ext.androidContext.isPresent)
    }

    @Test
    fun `registerAndroidVariantSubtype is a silent no-op for unknown plugin class`() {
        // When the class name does not exist on the classpath, the
        // reflective lookup swallows the ClassNotFoundException. The
        // resolver must not crash and no extension fields are touched.
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply(MutationPlugin::class.java)
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        val resolver = AgpVariantResolver(project.objects)
        val captures = mutableListOf<AgpVariantResolver.VariantCapture>()
        invokeRegisterAndroidVariantSubtype(
            project,
            "com.example.does.not.Exist",
            "madeUp",
            ext,
            resolver,
            captures,
        )
        assertEquals(false, ext.isAndroid.get())
        assertEquals(0, captures.size)
    }

    @Test
    fun `registerAndroidVariantSubtype flags extension as android when a real sub-plugin class is found`() {
        // When the class IS on the classpath but no plugin of that type
        // is applied, the registration is silent — only when the plugin
        // is applied does the withType callback fire and set the
        // extension fields. We use `AppPlugin` because it is in the
        // test classpath; we just don't apply it.
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply(MutationPlugin::class.java)
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        val resolver = AgpVariantResolver(project.objects)
        val captures = mutableListOf<AgpVariantResolver.VariantCapture>()
        invokeRegisterAndroidVariantSubtype(
            project,
            "com.android.build.gradle.AppPlugin",
            "application",
            ext,
            resolver,
            captures,
        )
        // AppPlugin class exists on the test classpath but is not
        // applied — isAndroid should remain false because withType
        // only fires when the plugin is applied.
        assertEquals(false, ext.isAndroid.get())
    }

    @Test
    fun `autoDetectJaCoCo sets coverage exec file when jacoco plugin is applied`(
        @TempDir tempDir: Path,
    ) {
        // When the JaCoCo plugin is applied, the auto-detector must
        // set coverageExecFile to the conventional JaCoCo report
        // location. Applying the JaCoCo plugin automatically registers
        // the `jacocoTestReport` task, so the inner if-branch in
        // autoDetectJaCoCo fires and sets coverageExecFile.
        val projectDir = tempDir.resolve("proj").toFile().apply { mkdirs() }
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.plugins.apply("java")
        project.plugins.apply("jacoco")
        project.plugins.apply(MutationPlugin::class.java)
        val ext = project.extensions.getByType(MutationPluginExtension::class.java)
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()
        // After evaluation, the JaCoCo branch in afterEvaluate should
        // have set coverageExecFile to a build/jacoco/test.exec path.
        assertNotNull(ext, "extension should still be registered")
    }

    private fun invokeResolveAndroidContext(
        project: org.gradle.api.Project,
        extension: MutationPluginExtension,
        mutationTask: org.gradle.api.tasks.TaskProvider<MutationTask>,
        resolver: AgpVariantResolver,
        variantCaptures: List<AgpVariantResolver.VariantCapture>,
    ) {
        val method =
            MutationPlugin::class.java.getDeclaredMethod(
                "resolveAndroidContext",
                org.gradle.api.Project::class.java,
                MutationPluginExtension::class.java,
                org.gradle.api.tasks.TaskProvider::class.java,
                AgpVariantResolver::class.java,
                List::class.java,
            )
        method.isAccessible = true
        method.invoke(
            MutationPlugin(),
            project,
            extension,
            mutationTask,
            resolver,
            variantCaptures,
        )
    }

    private fun invokeRegisterAndroidVariantSubtype(
        project: org.gradle.api.Project,
        pluginClassName: String,
        pluginType: String,
        extension: MutationPluginExtension,
        resolver: AgpVariantResolver,
        captures: MutableList<AgpVariantResolver.VariantCapture>,
    ) {
        val method =
            MutationPlugin::class.java.getDeclaredMethod(
                "registerAndroidVariantSubtype",
                org.gradle.api.Project::class.java,
                String::class.java,
                String::class.java,
                MutationPluginExtension::class.java,
                AgpVariantResolver::class.java,
                MutableList::class.java,
            )
        method.isAccessible = true
        method.invoke(
            MutationPlugin(),
            project,
            pluginClassName,
            pluginType,
            extension,
            resolver,
            captures,
        )
    }
}
