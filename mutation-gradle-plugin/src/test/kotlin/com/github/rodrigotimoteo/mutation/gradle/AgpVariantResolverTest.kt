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
    fun `resolve returns null when AGP is not on the classpath of the project`() {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        val ext =
            localProject.extensions.create(
                "mutationTest",
                MutationPluginExtension::class.java,
                localProject,
            )
        ext.isAndroid.set(true)
        ext.androidVariant.set("debug")
        val ctx = r.resolve(localProject, ext)
        assertThat(ctx).isNull()
    }

    @Test
    fun `resolve returns null when isAndroid extension flag is false`() {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        val ext =
            localProject.extensions.create(
                "mutationTest",
                MutationPluginExtension::class.java,
                localProject,
            )
        ext.isAndroid.set(false)
        ext.androidVariant.set("debug")
        val ctx = r.resolve(localProject, ext)
        assertThat(ctx).isNull()
    }

    @Test
    fun `resolve swallows NoClassDefFoundError and returns null when AGP is absent`() {
        val ctx = resolver.resolve(project, extensionFor(project))
        assertThat(ctx).isNull()
    }

    @Test
    fun `resolve returns null for a pure-JVM project with no Android plugin`() {
        project.plugins.apply("java")
        val ext = extensionFor(project)
        val ctx = resolver.resolve(project, ext)
        assertThat(ctx).isNull()
    }

    @Test
    fun `resolver reuses the same ObjectFactory across calls`() {
        val localProject = ProjectBuilder.builder().build()
        val r = AgpVariantResolver(localProject.objects)
        assertThat(r).isNotNull()
        // Second resolve should also be safe and return null
        val ext = extensionFor(localProject)
        assertThat(r.resolve(localProject, ext)).isNull()
    }

    private fun extensionFor(project: org.gradle.api.Project): MutationPluginExtension =
        project.extensions.findByType(MutationPluginExtension::class.java)
            ?: project.extensions.create(
                "mutationTest",
                MutationPluginExtension::class.java,
                project,
            )
}
