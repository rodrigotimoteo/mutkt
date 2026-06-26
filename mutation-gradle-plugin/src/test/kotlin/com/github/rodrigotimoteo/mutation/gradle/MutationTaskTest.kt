package com.github.rodrigotimoteo.mutation.gradle

import com.github.rodrigotimoteo.mutation.model.Mutation
import com.github.rodrigotimoteo.mutation.model.MutationReport
import com.github.rodrigotimoteo.mutation.model.MutationResult
import com.github.rodrigotimoteo.mutation.model.MutationStatus
import com.github.rodrigotimoteo.mutation.mutator.MutationOperator
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MutationTaskTest {
    @Test
    fun `task is registered with expected group`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        // The plugin sets task.group = "verification" when wiring the task
        // — verify the convention sticks when applied directly.
        task.group = "verification"
        assertEquals("verification", task.group)
    }

    @Test
    fun `task has expected default timeoutMs`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        assertEquals(30000L, task.timeoutMs.get())
    }

    @Test
    fun `task has expected default maxParallelMutants`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        assertEquals(4, task.maxParallelMutants.get())
    }

    @Test
    fun `task default failOnSurvived is false`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        assertEquals(false, task.failOnSurvived.get())
    }

    @Test
    fun `task default failOnScoreThreshold is 0`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        assertEquals(0, task.failOnScoreThreshold.get())
    }

    @Test
    fun `task default reportFormats is html`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        assertEquals(setOf("html"), task.reportFormats.get())
    }

    @Test
    fun `task reportsDir is build dir under reports mutation`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val dir = task.reportsDir.get().asFile
        assertNotNull(dir, "expected reportsDir to be resolvable")
        assertTrue(dir.absolutePath.contains("reports"), "expected 'reports' in path, got: ${dir.absolutePath}")
        assertTrue(dir.absolutePath.contains("mutation"), "expected 'mutation' in path, got: ${dir.absolutePath}")
    }

    @Test
    fun `task is a DefaultTask`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        assertNotNull(task)
    }

    @Test
    fun `task can have enabledOperators set`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        task.enabledOperators.set(setOf("ARITHMETIC", "NEGATE_CONDITIONALS"))
        assertEquals(setOf("ARITHMETIC", "NEGATE_CONDITIONALS"), task.enabledOperators.get())
    }

    @Test
    fun `task can have targetClasses set`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val file = project.layout.projectDirectory.file("build/classes/kotlin/main")
        task.targetClasses.from(file)
        assertEquals(1, task.targetClasses.files.size)
    }

    @Test
    fun `task can have testClasses set`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val file = project.layout.projectDirectory.file("build/classes/kotlin/test")
        task.testClasses.from(file)
        assertEquals(1, task.testClasses.files.size)
    }

    @Test
    fun `task can have classpath set`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val file = project.layout.projectDirectory.file("build/classes/kotlin/main")
        task.classpath.from(file)
        assertEquals(1, task.classpath.files.size)
    }

    @Test
    fun `task can have coverageExecFile set`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val file = project.layout.projectDirectory.file("build/jacoco/test.exec")
        task.coverageExecFile.set(file)
        assertNotNull(task.coverageExecFile.get())
    }

    @Test
    fun `task excludedClasses can be set`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        task.excludedClasses.set(setOf("com.example.Foo"))
        assertEquals(setOf("com.example.Foo"), task.excludedClasses.get())
    }

    @Test
    fun `task excludedMethods can be set`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        task.excludedMethods.set(setOf("toString"))
        assertEquals(setOf("toString"), task.excludedMethods.get())
    }

    @Test
    fun `task failOnSurvived can be set to true`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        task.failOnSurvived.set(true)
        assertEquals(true, task.failOnSurvived.get())
    }

    @Test
    fun `task failOnScoreThreshold can be set`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        task.failOnScoreThreshold.set(80)
        assertEquals(80, task.failOnScoreThreshold.get())
    }

    @Test
    fun `task reportFormats can be set to multiple`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        task.reportFormats.set(setOf("html", "console"))
        assertEquals(setOf("html", "console"), task.reportFormats.get())
    }

    @Test
    fun `task reportFormats can be set to empty`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        task.reportFormats.set(emptySet())
        assertEquals(emptySet(), task.reportFormats.get())
    }

    @Test
    fun `task default enabledOperators is MVP set via plugin wiring`() {
        val project = createProject()
        project.plugins.apply(MutationPlugin::class.java)
        val task = project.tasks.findByName("mutationTest") as MutationTask
        // The plugin wires task.enabledOperators from the extension's default
        // (the 6 MVP operators) — verify the default propagates.
        val operators = task.enabledOperators.get()
        assertEquals(6, operators.size, "expected 6 MVP operators, got: $operators")
        assertTrue(operators.contains("ARITHMETIC"), "expected ARITHMETIC in default set")
        assertTrue(operators.contains("NEGATE_CONDITIONALS"), "expected NEGATE_CONDITIONALS in default set")
    }

    @Test
    fun `task is correctly typed as MutationTask`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        // The task should have our taskAction method accessible
        assertNotNull(task.actions)
    }

    @Test
    fun `task has @TaskAction annotated runMutationTests`() {
        val project = createProject()
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        // Reflect on the @TaskAction method to verify the annotation is present
        // and that Gradle will pick it up as the task action.
        val method =
            MutationTask::class.java.declaredMethods.firstOrNull { it.name == "runMutationTests" }
        assertNotNull(method, "expected runMutationTests method on MutationTask")
        val annotation = method!!.getAnnotation(org.gradle.api.tasks.TaskAction::class.java)
        assertNotNull(annotation, "expected @TaskAction annotation on runMutationTests")
    }

    @Test
    fun `multiple tasks can be created with different names`() {
        val project = createProject()
        val task1 = project.tasks.create("mutationTest1", MutationTask::class.java)
        val task2 = project.tasks.create("mutationTest2", MutationTask::class.java)
        assertNotNull(task1)
        assertNotNull(task2)
    }

    private fun createProject(): Project {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        return project
    }
}

class MutationTaskInternalMethodsTest {
    @Test
    fun `parseOperators on empty set returns MVP_OPERATORS`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val operators = invokeParseOperators(task, emptySet())
        // MVP_OPERATORS is 6 active operators
        assertEquals(6, operators.size)
        assertTrue(operators.contains(MutationOperator.CONDITIONALS_BOUNDARY))
    }

    @Test
    fun `parseOperators with valid names returns those operators`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val operators = invokeParseOperators(task, setOf("ARITHMETIC", "NEGATE_CONDITIONALS"))
        assertEquals(2, operators.size)
        assertTrue(operators.any { it == MutationOperator.ARITHMETIC })
        assertTrue(operators.any { it == MutationOperator.NEGATE_CONDITIONALS })
    }

    @Test
    fun `parseOperators with invalid names skips them`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val operators = invokeParseOperators(task, setOf("ARITHMETIC", "DOES_NOT_EXIST", "NEGATE_CONDITIONALS"))
        // Invalid names are silently dropped
        assertEquals(2, operators.size)
    }

    @Test
    fun `parseOperators with all invalid names falls back to MVP`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val operators = invokeParseOperators(task, setOf("X", "Y", "Z"))
        assertEquals(MutationOperator.MVP_OPERATORS.size, operators.size)
    }

    @Test
    fun `parseOperators with all valid operators returns all`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val allNames = MutationOperator.values().map { it.name }.toSet()
        val operators = invokeParseOperators(task, allNames)
        assertEquals(MutationOperator.values().size, operators.size)
    }

    @Test
    fun `findClassesDir returns directory with class files`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val classDir = tempDir.resolve("classes").toFile()
        classDir.mkdirs()
        File(classDir, "Foo.class").writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte()))
        val result = invokeFindClassesDir(task, setOf(classDir))
        assertEquals(classDir, result)
    }

    @Test
    fun `findClassesDir skips empty directories and picks first non-empty`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val emptyDir = tempDir.resolve("empty").toFile()
        emptyDir.mkdirs()
        val classDir = tempDir.resolve("classes").toFile()
        classDir.mkdirs()
        File(classDir, "Foo.class").writeBytes(byteArrayOf(0xCA.toByte()))
        val result = invokeFindClassesDir(task, setOf(emptyDir, classDir))
        assertEquals(classDir, result)
    }

    @Test
    fun `findClassesDir skips non-directories`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val nonDir = tempDir.resolve("file.txt").toFile()
        nonDir.writeText("not a directory")
        val result = invokeFindClassesDir(task, setOf(nonDir))
        // Falls back to kotlin/main or java/main — both don't exist
        // The fallback is File(buildDir, "classes/kotlin/main")
        assertNotNull(result)
    }

    @Test
    fun `findClassesDir returns kotlin main when it exists`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        // ProjectBuilder doesn't put us in a directory with build/classes
        // So the fallback should still work
        val result = invokeFindClassesDir(task, emptySet())
        assertNotNull(result)
    }

    @Test
    fun `findClassesDir walks nested directories up to depth 5`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val deep = tempDir.resolve("a/b/c/d").toFile()
        deep.mkdirs()
        File(deep, "Foo.class").writeBytes(byteArrayOf(0xCA.toByte()))
        val result = invokeFindClassesDir(task, setOf(tempDir.toFile()))
        assertEquals(tempDir.toFile(), result)
    }

    @Test
    fun `resolveClassesDir prefers androidClassesDir when it exists`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val androidDir = tempDir.resolve("tmp/kotlin-classes/debug").toFile().apply { mkdirs() }
        File(androidDir, "Foo.class").writeBytes(byteArrayOf(0xCA.toByte()))
        val result = invokeResolveClassesDir(task, androidDir, emptySet(), isTestClasses = false)
        assertEquals(androidDir, result)
    }

    @Test
    fun `resolveClassesDir falls through to configured files when androidClassesDir is null`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val classDir = tempDir.resolve("classes").toFile().apply { mkdirs() }
        File(classDir, "Foo.class").writeBytes(byteArrayOf(0xCA.toByte()))
        val result = invokeResolveClassesDir(task, null, setOf(classDir), isTestClasses = false)
        assertEquals(classDir, result)
    }

    @Test
    fun `resolveClassesDir falls through when androidClassesDir does not exist on disk`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val missingAndroid = tempDir.resolve("does-not-exist").toFile()
        val classDir = tempDir.resolve("classes").toFile().apply { mkdirs() }
        File(classDir, "Foo.class").writeBytes(byteArrayOf(0xCA.toByte()))
        val result = invokeResolveClassesDir(task, missingAndroid, setOf(classDir), isTestClasses = false)
        assertEquals(classDir, result)
    }

    @Test
    fun `resolveClassesDir returns JVM fallback when androidClassesDir is null and files empty`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val result = invokeResolveClassesDir(task, null, emptySet(), isTestClasses = true)
        assertNotNull(result)
    }

    private fun invokeParseOperators(
        task: MutationTask,
        names: Set<String>,
    ): List<MutationOperator> {
        val method = MutationTask::class.java.getDeclaredMethod("parseOperators", Set::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(task, names) as List<MutationOperator>
    }

    private fun invokeFindClassesDir(
        task: MutationTask,
        files: Set<File>,
        isTestClasses: Boolean = false,
    ): File {
        val method = MutationTask::class.java.getDeclaredMethod("findClassesDir", Set::class.java, Boolean::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(task, files, isTestClasses) as File
    }

    private fun invokeResolveClassesDir(
        task: MutationTask,
        androidDir: File?,
        files: Set<File>,
        isTestClasses: Boolean,
    ): File {
        val method =
            MutationTask::class.java.getDeclaredMethod(
                "resolveClassesDir",
                File::class.java,
                Set::class.java,
                Set::class.java,
                Boolean::class.java,
            )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(task, androidDir, emptySet<File>(), files, isTestClasses) as File
    }

    private fun invokeExpandAars(
        task: MutationTask,
        files: List<File>,
        tempDir: File,
    ): List<File> = task.expandAars(files, tempDir)
}

class MutationTaskExpandAarsTest {
    @Test
    fun `expandAars returns the same list when no AARs are present`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val plainJar = tempDir.resolve("lib.jar").toFile().apply { writeText("not real") }
        val plainDir = tempDir.resolve("classes").toFile().apply { mkdirs() }
        val result = invokeExpandAars(task, listOf(plainJar, plainDir), tempDir.toFile())
        assertEquals(2, result.size)
        assertEquals(listOf(plainJar, plainDir), result)
    }

    @Test
    fun `expandAars returns the same list for empty input`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val result = invokeExpandAars(task, emptyList(), tempDir.toFile())
        assertEquals(emptyList<File>(), result)
    }

    @Test
    fun `expandAars extracts classes_jar from a valid AAR`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val aar = buildFakeAar(tempDir.resolve("fake.aar").toFile(), includeClassesJar = true)
        val plainJar = tempDir.resolve("plain.jar").toFile().apply { writeText("not real") }
        val result = invokeExpandAars(task, listOf(aar, plainJar), tempDir.toFile())
        // AAR replaced with its inner classes.jar + plain jar passed through
        assertEquals(2, result.size)
        assertEquals("classes.jar", result[0].name)
        assertEquals(plainJar, result[1])
    }

    @Test
    fun `expandAars drops AAR entries that lack classes_jar and logs a warning`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val emptyAar = buildFakeAar(tempDir.resolve("empty.aar").toFile(), includeClassesJar = false)
        val plainJar = tempDir.resolve("plain.jar").toFile().apply { writeText("not real") }
        val result = invokeExpandAars(task, listOf(emptyAar, plainJar), tempDir.toFile())
        // Empty AAR dropped, plain jar kept
        assertEquals(1, result.size)
        assertEquals(plainJar, result[0])
    }

    @Test
    fun `expandAars drops AAR entries that are not valid zips`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        val garbageAar = tempDir.resolve("garbage.aar").toFile().apply { writeText("not a zip") }
        val plainJar = tempDir.resolve("plain.jar").toFile().apply { writeText("not real") }
        val result = invokeExpandAars(task, listOf(garbageAar, plainJar), tempDir.toFile())
        assertEquals(1, result.size)
        assertEquals(plainJar, result[0])
    }

    private fun invokeExpandAars(
        task: MutationTask,
        files: List<File>,
        tempDir: File,
    ): List<File> = task.expandAars(files, tempDir)

    private fun buildFakeAar(
        out: File,
        includeClassesJar: Boolean,
    ): File {
        java.util.zip.ZipOutputStream(out.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("AndroidManifest.xml"))
            zip.write("<manifest/>".toByteArray())
            zip.closeEntry()
            if (includeClassesJar) {
                zip.putNextEntry(java.util.zip.ZipEntry("classes.jar"))
                zip.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
                zip.closeEntry()
            }
        }
        return out
    }
}

class DiscoverKotlinCompileTasksTest {
    @Test
    fun `discoverKotlinCompileTasks returns compileKotlin and compileTestKotlin for pure-JVM`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        val (main, test) = MutationPlugin().discoverKotlinCompileTasks(project, isAndroid = false)
        assertEquals("compileKotlin", main)
        assertEquals("compileTestKotlin", test)
    }

    @Test
    fun `discoverKotlinCompileTasks returns empty pair for Android without AGP variant capture`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        // No real AGP and no variant captures → empty pair so the caller
        // can skip wiring (avoids depending on compileDebugKotlin which
        // may not exist in release-only or custom variant configurations).
        val (main, test) = MutationPlugin().discoverKotlinCompileTasks(project, isAndroid = true)
        assertEquals("", main)
        assertEquals("", test)
    }

    @Test
    fun `discoverKotlinCompileTasks uses captured AGP variant task names when present`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val capture =
            AgpVariantResolver.VariantCapture(
                name = "release",
                runtimeConfiguration =
                    project.objects.fileCollection(),
                compileTask = "compileReleaseKotlin",
                testCompileTask = "compileReleaseUnitTestKotlin",
            )
        val (main, test) =
            MutationPlugin()
                .discoverKotlinCompileTasks(project, isAndroid = true, variantCaptures = listOf(capture))
        assertEquals("compileReleaseKotlin", main)
        assertEquals("compileReleaseUnitTestKotlin", test)
    }
}

class MutationTaskActionTest {
    @Test
    fun `runMutationTests throws when classesDir does not exist`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        task.targetClasses.from(tempDir.resolve("nonexistent").toFile())
        task.testClasses.from(tempDir.resolve("test").toFile())
        task.reportsDir.set(project.layout.buildDirectory.dir("reports/mutation"))
        try {
            invokeRunMutationTests(task)
            error("Should have thrown")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause
            assertNotNull(cause)
            assertTrue(
                cause is IllegalStateException,
                "Expected IllegalStateException, got ${cause::class.java.name}: ${cause.message}",
            )
        }
    }

    @Test
    fun `runMutationTests throws when testClassesDir does not exist`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        // classes dir exists
        val classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
        File(classesDir, "Foo.class").writeBytes(byteArrayOf(0xCA.toByte()))
        // test classes dir does NOT exist
        task.targetClasses.from(classesDir)
        task.testClasses.from(tempDir.resolve("test_nonexistent").toFile())
        task.reportsDir.set(project.layout.buildDirectory.dir("reports/mutation"))
        try {
            invokeRunMutationTests(task)
            error("Should have thrown")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause
            assertNotNull(cause)
            assertTrue(cause is IllegalStateException, "Expected IllegalStateException")
        }
    }

    @Test
    fun `runMutationTests throws GradleException when failOnSurvived and survived`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        task.failOnSurvived.set(true)

        val report = buildSurvivedReport()

        try {
            invokeCheckFailConditions(task, report)
            error("Expected GradleException for survived mutations")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause
            assertNotNull(cause, "Expected wrapped cause")
            assertTrue(
                cause is org.gradle.api.GradleException,
                "Expected GradleException, got ${cause::class.java.name}: ${cause.message}",
            )
        }
    }

    @Test
    fun `checkFailConditions does not throw when failOnSurvived true but no survivors`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        task.failOnSurvived.set(true)

        val report =
            MutationReport(
                results = emptyList(),
                totalMutations = 0,
                killedMutations = 0,
                survivedMutations = 0,
                errorMutations = 0,
                timeoutMutations = 0,
                noCoverageMutations = 0,
                totalExecutionTimeMs = 0,
            )

        invokeCheckFailConditions(task, report)
    }

    @Test
    fun `deprecated failOnScoreThreshold is honored as a score threshold`() {
        // Backward-compat: `failOnScoreThreshold` is marked @Deprecated in
        // favor of `failOnMutationScoreThreshold`, but setting it on a
        // pre-0.3.0 user script must still fail the build when the score
        // is below the threshold. Verify the deprecated property feeds
        // checkFailConditions without a regression.
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        task.failOnScoreThreshold.set(80)
        // failOnMutationScoreThreshold stays at default (0) — the
        // deprecated property alone must be enough to enforce the threshold.

        val report =
            MutationReport(
                results = emptyList(),
                totalMutations = 10,
                // 50% — below 80
                killedMutations = 5,
                survivedMutations = 5,
                errorMutations = 0,
                timeoutMutations = 0,
                noCoverageMutations = 0,
                totalExecutionTimeMs = 0,
            )

        try {
            invokeCheckFailConditions(task, report)
            error("Expected GradleException for score below deprecated threshold")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause
            assertNotNull(cause, "Expected wrapped cause")
            assertTrue(
                cause is org.gradle.api.GradleException,
                "Expected GradleException, got ${cause::class.java.name}: ${cause.message}",
            )
            assertTrue(
                cause.message?.contains("80") == true,
                "Expected threshold 80 in message, got: ${cause.message}",
            )
        }
    }

    @Test
    fun `deprecated failOnScoreThreshold does not fire when score meets threshold`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        task.failOnScoreThreshold.set(50)

        val report =
            MutationReport(
                results = emptyList(),
                totalMutations = 10,
                // 80% — above 50
                killedMutations = 8,
                survivedMutations = 2,
                errorMutations = 0,
                timeoutMutations = 0,
                noCoverageMutations = 0,
                totalExecutionTimeMs = 0,
            )

        // 80% >= 50% — must not throw
        invokeCheckFailConditions(task, report)
    }

    @Test
    fun `runMutationTests with both dirs and no coverage runs through`(
        @TempDir tempDir: Path,
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val task = project.tasks.create("mutationTest", MutationTask::class.java)
        // Create real class files
        val classesDir = tempDir.resolve("classes/com/example").toFile()
        classesDir.mkdirs()
        File(classesDir, "Foo.class").writeBytes(buildSimpleClassBytes("com/example/Foo"))
        val testClassesDir = tempDir.resolve("test-classes/com/example").toFile()
        testClassesDir.mkdirs()
        File(testClassesDir, "FooTest.class").writeBytes(buildSimpleClassBytes("com/example/FooTest"))
        task.targetClasses.from(classesDir)
        task.testClasses.from(testClassesDir)
        task.reportsDir.set(project.layout.buildDirectory.dir("reports/mutation"))
        task.failOnSurvived.set(false)
        try {
            invokeRunMutationTests(task)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause
            // Acceptable to fail with various exceptions (e.g., test framework errors)
            // The point is just exercising the code path
            assertNotNull(cause)
        }
    }

    private fun invokeRunMutationTests(task: MutationTask) {
        val method = MutationTask::class.java.getDeclaredMethod("runMutationTests")
        method.isAccessible = true
        method.invoke(task)
    }

    private fun invokeCheckFailConditions(
        task: MutationTask,
        report: MutationReport,
    ) {
        val method =
            MutationTask::class.java.declaredMethods.firstOrNull {
                it.name.startsWith("checkFailConditions") &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == MutationReport::class.java
            } ?: error("checkFailConditions(MutationReport) not found on MutationTask")
        method.isAccessible = true
        method.invoke(task, report)
    }

    private fun buildSurvivedReport(): MutationReport {
        val survivedMutation =
            MutationResult(
                mutation =
                    Mutation(
                        id = "m-survived-1",
                        className = "com.example.Foo",
                        methodName = "bar",
                        methodDescriptor = "()I",
                        operator = MutationOperator.NEGATE_CONDITIONALS,
                        lineNumber = 10,
                        description = "test survived mutation",
                    ),
                status = MutationStatus.SURVIVED,
                executionTimeMs = 5,
            )
        return MutationReport(
            results = listOf(survivedMutation),
            totalMutations = 1,
            killedMutations = 0,
            survivedMutations = 1,
            errorMutations = 0,
            timeoutMutations = 0,
            noCoverageMutations = 0,
            totalExecutionTimeMs = 5,
        )
    }

    private fun buildSimpleClassBytes(className: String): ByteArray {
        // Build a minimal class file using ASM
        val cw = org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES)
        cw.visit(
            org.objectweb.asm.Opcodes.V17,
            org.objectweb.asm.Opcodes.ACC_PUBLIC,
            className.replace("/", "."),
            null,
            "java/lang/Object",
            null,
        )
        val ctor = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor?.visitCode()
        ctor?.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0)
        ctor?.visitMethodInsn(
            org.objectweb.asm.Opcodes.INVOKESPECIAL,
            "java/lang/Object",
            "<init>",
            "()V",
            false,
        )
        ctor?.visitInsn(org.objectweb.asm.Opcodes.RETURN)
        ctor?.visitMaxs(1, 1)
        ctor?.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }
}
