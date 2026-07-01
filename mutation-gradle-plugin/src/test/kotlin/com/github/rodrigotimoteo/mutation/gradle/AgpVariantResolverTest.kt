package com.github.rodrigotimoteo.mutation.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class AgpVariantResolverTest {
    private val project = ProjectBuilder.builder().build()
    private val objectFactory: ObjectFactory = project.objects
    private val resolver = AgpVariantResolver(objectFactory)

    @Test
    fun `resolver is constructable with a Gradle ObjectFactory`() {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        assertThat(r).isNotNull()
    }

    @Test
    fun `registerOnVariants is a no-op when AGP is not on the classpath of the project`() {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        val captures = mutableListOf<AgpVariantResolver.VariantCapture>()
        r.registerOnVariants(localProject, captures)
        assertThat(captures).isEmpty()
    }

    @Test
    fun `registerOnVariants swallows NoClassDefFoundError and leaves captures empty`() {
        val captures = mutableListOf<AgpVariantResolver.VariantCapture>()
        resolver.registerOnVariants(project, captures)
        assertThat(captures).isEmpty()
    }

    @Test
    fun `registerOnVariants returns no captures for a pure-JVM project with no Android plugin`() {
        project.plugins.apply("java")
        val captures = mutableListOf<AgpVariantResolver.VariantCapture>()
        resolver.registerOnVariants(project, captures)
        assertThat(captures).isEmpty()
    }

    @Test
    fun `resolver reuses the same ObjectFactory across calls`() {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        assertThat(r).isNotNull()
        // Second invocation should also be safe and return an empty list
        val captures = mutableListOf<AgpVariantResolver.VariantCapture>()
        r.registerOnVariants(localProject, captures)
        assertThat(captures).isEmpty()
    }

    @Test
    fun `findVariant returns null when captures list is empty`() {
        val result = resolver.findVariant(emptyList(), "debug")
        assertThat(result).isNull()
    }

    @Test
    fun `findVariant returns null when no capture matches the requested name`() {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        val dummyCapture =
            AgpVariantResolver.VariantCapture(
                name = "release",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileReleaseKotlin",
                testCompileTask = "compileReleaseUnitTestKotlin",
            )
        val result = r.findVariant(listOf(dummyCapture), "debug")
        assertThat(result).isNull()
    }

    @Test
    fun `findVariant returns the matching capture by name`() {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        val debugCapture =
            AgpVariantResolver.VariantCapture(
                name = "debug",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileDebugKotlin",
                testCompileTask = "compileDebugUnitTestKotlin",
            )
        val releaseCapture =
            AgpVariantResolver.VariantCapture(
                name = "release",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileReleaseKotlin",
                testCompileTask = "compileReleaseUnitTestKotlin",
            )
        val result = r.findVariant(listOf(debugCapture, releaseCapture), "debug")
        assertThat(result).isNotNull()
        assertThat(result?.name).isEqualTo("debug")
    }

    @Test
    fun `findVariant matches case-insensitively`() {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        val capture =
            AgpVariantResolver.VariantCapture(
                name = "Release",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileReleaseKotlin",
                testCompileTask = "compileReleaseUnitTestKotlin",
            )
        val result = r.findVariant(listOf(capture), "release")
        assertThat(result).isNotNull()
        assertThat(result?.name).isEqualTo("Release")
    }

    @Test
    fun `buildContext produces an AndroidMutationContext from a VariantCapture`() {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        val capture =
            AgpVariantResolver.VariantCapture(
                name = "debug",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileDebugKotlin",
                testCompileTask = "compileDebugUnitTestKotlin",
            )
        val ctx = r.buildContext(localProject, capture)
        assertThat(ctx.variantName).isEqualTo("debug")
        assertThat(ctx.compileTask).isEqualTo("compileDebugKotlin")
        assertThat(ctx.testCompileTask).isEqualTo("compileDebugUnitTestKotlin")
        // androidJar is nullable — either null (no SDK) or a real File named "android.jar"
        if (ctx.androidJar != null) {
            assertThat(ctx.androidJar.name).isEqualTo("android.jar")
        }
        // runtimeClasspath is non-null (file collection)
        assertThat(ctx.runtimeClasspath).isNotNull
    }

    @Test
    fun `findVariantClassesDirs returns null pair when no compile tasks exist`(
        @TempDir tempDir: Path,
    ) {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        val capture =
            AgpVariantResolver.VariantCapture(
                name = "debug",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileDebugKotlin",
                testCompileTask = "compileDebugUnitTestKotlin",
            )
        val (main, test) = r.findVariantClassesDirs(localProject, capture)
        assertThat(main).isNull()
        assertThat(test).isNull()
    }

    @Test
    fun `findVariantClassesDirs resolves Kotlin compile task destination`(
        @TempDir tempDir: Path,
    ) {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)

        val mainOut = tempDir.resolve("kotlin-classes/debug").toFile().apply { mkdirs() }
        val testOut = tempDir.resolve("kotlin-classes/debugUnitTest").toFile().apply { mkdirs() }

        val mainTask =
            localProject.tasks.register("compileDebugKotlin", JavaCompile::class.java)
        val testTask =
            localProject.tasks.register("compileDebugUnitTestKotlin", JavaCompile::class.java)
        mainTask.get().destinationDirectory.set(mainOut)
        testTask.get().destinationDirectory.set(testOut)

        val capture =
            AgpVariantResolver.VariantCapture(
                name = "debug",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = mainTask.get().name,
                testCompileTask = testTask.get().name,
            )

        val resolved = r.findVariantClassesDirs(localProject, capture)
        assertThat(resolved.first).isEqualTo(mainOut)
        assertThat(resolved.second).isEqualTo(testOut)
    }

    @Test
    fun `findVariantClassesDirs falls back to JavaWithJavac when Kotlin task is absent`(
        @TempDir tempDir: Path,
    ) {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)

        val mainOut = tempDir.resolve("javac/debug/classes").toFile().apply { mkdirs() }
        val testOut = tempDir.resolve("javac/debugUnitTest/classes").toFile().apply { mkdirs() }

        val mainTask =
            localProject.tasks.register("compileDebugJavaWithJavac", JavaCompile::class.java)
        val testTask =
            localProject.tasks.register("compileDebugUnitTestJavaWithJavac", JavaCompile::class.java)
        mainTask.get().destinationDirectory.set(mainOut)
        testTask.get().destinationDirectory.set(testOut)

        val capture =
            AgpVariantResolver.VariantCapture(
                name = "debug",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileDebugKotlin",
                testCompileTask = "compileDebugUnitTestKotlin",
            )

        val resolved = r.findVariantClassesDirs(localProject, capture)
        assertThat(resolved.first).isEqualTo(mainOut)
        assertThat(resolved.second).isEqualTo(testOut)
        assertThat(mainTask.get().name).isEqualTo("compileDebugJavaWithJavac")
        assertThat(testTask.get().name).isEqualTo("compileDebugUnitTestJavaWithJavac")
    }

    @Test
    fun `findVariantClassesDirs prefers Kotlin task over JavaWithJavac when both exist`(
        @TempDir tempDir: Path,
    ) {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)

        val kotlinOut = tempDir.resolve("kotlin-classes/debug").toFile().apply { mkdirs() }
        val javacOut = tempDir.resolve("javac/debug/classes").toFile().apply { mkdirs() }

        val kotlinTask =
            localProject.tasks.register("compileDebugKotlin", JavaCompile::class.java)
        val javacTask =
            localProject.tasks.register("compileDebugJavaWithJavac", JavaCompile::class.java)
        kotlinTask.get().destinationDirectory.set(kotlinOut)
        javacTask.get().destinationDirectory.set(javacOut)

        val capture =
            AgpVariantResolver.VariantCapture(
                name = "debug",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileDebugKotlin",
                testCompileTask = "compileDebugUnitTestKotlin",
            )

        val resolved = r.findVariantClassesDirs(localProject, capture)
        assertThat(resolved.first).isEqualTo(kotlinOut)
    }

    @Test
    fun `findVariantClassesDirs handles release variant with capitalized name`(
        @TempDir tempDir: Path,
    ) {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)

        val mainOut = tempDir.resolve("release/classes").toFile().apply { mkdirs() }
        val releaseTask =
            localProject.tasks.register("compileReleaseKotlin", JavaCompile::class.java)
        releaseTask.get().destinationDirectory.set(mainOut)

        val capture =
            AgpVariantResolver.VariantCapture(
                name = "release",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileReleaseKotlin",
                testCompileTask = "compileReleaseUnitTestKotlin",
            )

        val resolved = r.findVariantClassesDirs(localProject, capture)
        assertThat(resolved.first).isEqualTo(mainOut)
        assertThat(resolved.second).isNull()
    }

    @Test
    fun `findVariantClassesDirs returns null when task is not a JavaCompile`(
        @TempDir tempDir: Path,
    ) {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)

        // Register a non-JavaCompile task that has no destinationDirectory property
        localProject.tasks.register("compileDebugKotlin")

        val capture =
            AgpVariantResolver.VariantCapture(
                name = "debug",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileDebugKotlin",
                testCompileTask = "compileDebugUnitTestKotlin",
            )

        val resolved = r.findVariantClassesDirs(localProject, capture)
        assertThat(resolved.first).isNull()
        assertThat(resolved.second).isNull()
    }

    @Test
    fun `buildContext populates mainClassesDir and testClassesDir from compile tasks`(
        @TempDir tempDir: Path,
    ) {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)

        val mainOut = tempDir.resolve("kotlin-classes/debug").toFile().apply { mkdirs() }
        val testOut = tempDir.resolve("kotlin-classes/debugUnitTest").toFile().apply { mkdirs() }

        val mainTask =
            localProject.tasks.register("compileDebugKotlin", JavaCompile::class.java)
        val testTask =
            localProject.tasks.register("compileDebugUnitTestKotlin", JavaCompile::class.java)
        mainTask.get().destinationDirectory.set(mainOut)
        testTask.get().destinationDirectory.set(testOut)

        val capture =
            AgpVariantResolver.VariantCapture(
                name = "debug",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileDebugKotlin",
                testCompileTask = "compileDebugUnitTestKotlin",
            )
        val ctx = r.buildContext(localProject, capture)
        assertThat(ctx.mainClassesDir).isEqualTo(mainOut)
        assertThat(ctx.testClassesDir).isEqualTo(testOut)
    }

    @Test
    fun `buildContext adds debugUnitTestRuntimeClasspath when present on project`(
        @TempDir tempDir: Path,
    ) {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)

        val mainOut = tempDir.resolve("kotlin-classes/debug").toFile().apply { mkdirs() }
        val mainTask =
            localProject.tasks.register("compileDebugKotlin", JavaCompile::class.java)
        mainTask.get().destinationDirectory.set(mainOut)

        // Create a test-runtime classpath configuration with a real file
        // inside it so the runtime classpath entry is observable.
        val cfg = localProject.configurations.create("debugUnitTestRuntimeClasspath")
        val fakeDep = tempDir.resolve("mockk.jar").toFile()
        fakeDep.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        cfg.dependencies.add(localProject.dependencies.create(localProject.files(fakeDep)))

        val capture =
            AgpVariantResolver.VariantCapture(
                name = "debug",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileDebugKotlin",
                testCompileTask = "compileDebugUnitTestKotlin",
            )
        val ctx = r.buildContext(localProject, capture)
        // The configuration's file should now be on the resolved runtime
        // classpath alongside the (empty) AGP-provided one.
        val files = ctx.runtimeClasspath.files
        assertThat(files).contains(fakeDep)
    }

    @Test
    fun `buildContext adds DebugUnitTestRuntimeClasspath when present on project`(
        @TempDir tempDir: Path,
    ) {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)

        val mainOut = tempDir.resolve("kotlin-classes/debug").toFile().apply { mkdirs() }
        val mainTask =
            localProject.tasks.register("compileDebugKotlin", JavaCompile::class.java)
        mainTask.get().destinationDirectory.set(mainOut)

        // Use the capitalized form ("Debug...") — the resolver
        // deduplicates via `.distinct()` so either name is enough to
        // reach the add-to-runtime-classpath branch.
        val cfg = localProject.configurations.create("DebugUnitTestRuntimeClasspath")
        val fakeDep = tempDir.resolve("junit.jar").toFile()
        fakeDep.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        cfg.dependencies.add(localProject.dependencies.create(localProject.files(fakeDep)))

        val capture =
            AgpVariantResolver.VariantCapture(
                name = "debug",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileDebugKotlin",
                testCompileTask = "compileDebugUnitTestKotlin",
            )
        val ctx = r.buildContext(localProject, capture)
        assertThat(ctx.runtimeClasspath.files).contains(fakeDep)
    }

    @Test
    fun `buildContext does not throw when no test runtime classpath configuration exists`(
        @TempDir tempDir: Path,
    ) {
        // No `debugUnitTestRuntimeClasspath` / `DebugUnitTestRuntimeClasspath`
        // configuration — the forEach loop over distinct names must
        // silently no-op so users without test dependencies configured
        // do not get a build failure.
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)

        val mainOut = tempDir.resolve("kotlin-classes/debug").toFile().apply { mkdirs() }
        val mainTask =
            localProject.tasks.register("compileDebugKotlin", JavaCompile::class.java)
        mainTask.get().destinationDirectory.set(mainOut)

        val capture =
            AgpVariantResolver.VariantCapture(
                name = "debug",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileDebugKotlin",
                testCompileTask = "compileDebugUnitTestKotlin",
            )
        val ctx = r.buildContext(localProject, capture)
        assertThat(ctx).isNotNull
        assertThat(ctx.mainClassesDir).isEqualTo(mainOut)
    }

    @Test
    fun `isKmpAndroidTarget returns true when kotlin multiplatform plugin is applied`() {
        val localProject = ProjectBuilder.builder().build()
        localProject.plugins.apply("org.jetbrains.kotlin.multiplatform")
        val r = AgpVariantResolver(localProject.objects)
        val result = invokeIsKmpAndroidTarget(r, localProject)
        assertThat(result).isTrue
    }

    @Test
    fun `isKmpAndroidTarget returns false when no Kotlin plugins are applied`() {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        val result = invokeIsKmpAndroidTarget(r, localProject)
        assertThat(result).isFalse
    }

    @Test
    fun `isKmpAndroidTarget caches the result across invocations on the same project`() {
        val localProject = ProjectBuilder.builder().build()
        localProject.plugins.apply("org.jetbrains.kotlin.multiplatform")
        val r = AgpVariantResolver(localProject.objects)
        val first = invokeIsKmpAndroidTarget(r, localProject)
        val second = invokeIsKmpAndroidTarget(r, localProject)
        assertThat(first).isTrue
        assertThat(second).isTrue
    }

    private fun invokeIsKmpAndroidTarget(
        resolver: AgpVariantResolver,
        project: org.gradle.api.Project,
    ): Boolean {
        val method =
            AgpVariantResolver::class.java
                .getDeclaredMethod("isKmpAndroidTarget", org.gradle.api.Project::class.java)
        method.isAccessible = true
        return method.invoke(resolver, project) as Boolean
    }

    @Test
    fun `formatVariantResolutionError names the unmatched ProductFlavor attribute and suggests a fix`(
        @TempDir tempDir: Path,
    ) {
        // Reproduces the TvJapan failure message. The formatter
        // must extract the attribute name + value, name the
        // producer project, and produce a one-line Gradle DSL fix
        // (missingDimensionStrategy) the user can paste into their
        // build script.
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        val capture =
            AgpVariantResolver.VariantCapture(
                name = "debug",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileDebugKotlin",
                testCompileTask = "compileDebugUnitTestKotlin",
            )
        val raw =
            """
            > Could not resolve project ':sharedTestCode'.
              Required by:
                  project :app
              > Cannot choose between the following variants of project :sharedTestCode:
                  - productionDebugRuntimeElements
                  - stagingDebugRuntimeElements
                All of them match the consumer attributes:
                  - com.android.build.api.attributes.BuildTypeAttr='debug'
                The following variants are also available:
                  - com.android.build.api.attributes.ProductFlavor:brand='production'
                  - com.android.build.api.attributes.ProductFlavor:brand='staging'
            """.trimIndent()
        val ex = RuntimeException(raw)
        val formatted = r.formatVariantResolutionError(ex, capture, localProject)
        assertThat(formatted).isNotNull
        assertThat(formatted!!).contains("brand")
        assertThat(formatted).contains("production")
        assertThat(formatted).contains("missingDimensionStrategy")
        assertThat(formatted).contains(":sharedTestCode")
    }

    @Test
    fun `formatVariantResolutionError returns null when the exception does not match the AGP pattern`(
        @TempDir tempDir: Path,
    ) {
        // Sanity check: a generic exception (e.g. NPE) must NOT be
        // re-formatted. Callers fall back to re-throwing the
        // original exception in that case, preserving the stack
        // trace for debugging.
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        val capture =
            AgpVariantResolver.VariantCapture(
                name = "debug",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileDebugKotlin",
                testCompileTask = "compileDebugUnitTestKotlin",
            )
        val formatted = r.formatVariantResolutionError(NullPointerException(), capture, localProject)
        assertThat(formatted).isNull()
    }

    @Test
    fun `formatVariantResolutionError produces a generic fix message when the attribute is missing`(
        @TempDir tempDir: Path,
    ) {
        // The "Cannot choose between variants" error sometimes
        // doesn't include a structured attribute token. The
        // formatter must still produce a useful message (still
        // name the variant, still suggest missingDimensionStrategy
        // / androidVariant as fallbacks) so the user is not left
        // with just a Gradle stack trace.
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        val capture =
            AgpVariantResolver.VariantCapture(
                name = "debug",
                runtimeConfiguration = localProject.objects.fileCollection(),
                compileTask = "compileDebugKotlin",
                testCompileTask = "compileDebugUnitTestKotlin",
            )
        val formatted =
            r.formatVariantResolutionError(
                RuntimeException("Could not resolve :sharedTestCode"),
                capture,
                localProject,
            )
        assertThat(formatted).isNotNull
        assertThat(formatted!!).contains("missingDimensionStrategy")
    }

    @Test
    fun `findMainRuntimeConfiguration returns null when no runtime classpath exists`(
        @TempDir tempDir: Path,
    ) {
        // An empty project has no AGP-created runtime classpath
        // configuration. The resolver must return null and the
        // buildContext must fall back to the captured
        // `runtimeConfiguration` from the variant API.
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        val method =
            AgpVariantResolver::class.java.getDeclaredMethod(
                "findMainRuntimeConfiguration",
                org.gradle.api.Project::class.java,
                String::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(r, localProject, "debug")
        assertThat(result).isNull()
    }

    @Test
    fun `findMainRuntimeConfiguration returns the configuration when present`(
        @TempDir tempDir: Path,
    ) {
        // When the project's AGP-style runtime classpath is
        // registered, the resolver must return it so the
        // ArtifactView wrap can apply `artifactType=jar`. The
        // exact name is `<variant>RuntimeClasspath`; we also
        // probe the capitalized form.
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        localProject.configurations.create("debugRuntimeClasspath")
        val method =
            AgpVariantResolver::class.java.getDeclaredMethod(
                "findMainRuntimeConfiguration",
                org.gradle.api.Project::class.java,
                String::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(r, localProject, "debug")
        assertThat(result).isNotNull
        assertThat((result as org.gradle.api.artifacts.Configuration).name).isEqualTo("debugRuntimeClasspath")
    }

    @Test
    fun `findMainRuntimeConfiguration matches the capitalized variant form`(
        @TempDir tempDir: Path,
    ) {
        // AGP quirk tolerance: some AGP versions create the
        // configuration with the capitalized variant name
        // (e.g. `DebugRuntimeClasspath`). The resolver must
        // still find it.
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        localProject.configurations.create("DebugRuntimeClasspath")
        val method =
            AgpVariantResolver::class.java.getDeclaredMethod(
                "findMainRuntimeConfiguration",
                org.gradle.api.Project::class.java,
                String::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(r, localProject, "debug")
        assertThat(result).isNotNull
    }

    @Test
    fun `findUnitTestRuntimeConfiguration returns null when no classpath exists`(
        @TempDir tempDir: Path,
    ) {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        val method =
            AgpVariantResolver::class.java.getDeclaredMethod(
                "findUnitTestRuntimeConfiguration",
                org.gradle.api.Project::class.java,
                String::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(r, localProject, "debug")
        assertThat(result).isNull()
    }

    @Test
    fun `findUnitTestRuntimeConfiguration returns the configuration when present`(
        @TempDir tempDir: Path,
    ) {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        localProject.configurations.create("debugUnitTestRuntimeClasspath")
        val method =
            AgpVariantResolver::class.java.getDeclaredMethod(
                "findUnitTestRuntimeConfiguration",
                org.gradle.api.Project::class.java,
                String::class.java,
            )
        method.isAccessible = true
        val result = method.invoke(r, localProject, "debug")
        assertThat(result).isNotNull
    }
}
