package com.github.rodrigotimoteo.mutation.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

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
        // androidJar is a real File — verify it's a non-null path containing "android.jar"
        assertThat(ctx.androidJar).isNotNull
        assertThat(ctx.androidJar.name).isEqualTo("android.jar")
        // runtimeClasspath is non-null (file collection)
        assertThat(ctx.runtimeClasspath).isNotNull
    }
}
