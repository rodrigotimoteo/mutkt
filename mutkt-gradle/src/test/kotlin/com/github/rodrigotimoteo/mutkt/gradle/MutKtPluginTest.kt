package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class MutKtPluginTest {
    private fun project(vararg plugins: String): Project =
        ProjectBuilder.builder().build().also { project ->
            plugins.forEach(project.pluginManager::apply)
            project.pluginManager.apply(MutKtPlugin::class.java)
        }

    private fun Project.mutkt(): MutKtExtension = extensions.getByType(MutKtExtension::class.java)

    @Test
    fun `registers the mutkt extension with conventions`() {
        val project = project("java")
        val mutkt = project.mutkt()

        assertFalse(mutkt.threads.isPresent)
        assertEquals(10_000L, mutkt.timeoutMs.get())
        assertEquals(setOf("junit-jupiter"), mutkt.engines.get())
        assertTrue(mutkt.targetClasses.get().isEmpty())
        assertTrue(mutkt.operators.get().isEmpty())
        assertFalse(mutkt.maxMutantsPerClass.isPresent)
        assertFalse(mutkt.failBelowScore.isPresent)
        project.evaluate()
    }

    @Test
    fun `reports default to console only and are configurable`() {
        val project = project("java")
        val mutkt = project.mutkt()
        assertTrue(mutkt.reports.console.get())

        mutkt.reports { it.console.set(false) }
        assertFalse(mutkt.reports.console.get())
    }

    @Test
    fun `fails without a Java or Kotlin plugin`() {
        val failure = assertThrows<GradleException> { project().evaluate() }
        val messages = generateSequence<Throwable>(failure) { it.cause }.map { it.message.orEmpty() }
        assertTrue(messages.any { "MutKt requires the Java or Kotlin JVM plugin" in it })
    }

    @Test
    fun `accepts the Java plugin applied after MutKt`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(MutKtPlugin::class.java)
        project.pluginManager.apply("java")
        project.evaluate()
        assertNotNull(project.extensions.findByName("mutkt"))
    }

    @Test
    fun `docs and extension list the same properties`() {
        val documented =
            File(System.getProperty("mutkt.dslDoc"))
                .readLines()
                .filter { it.startsWith("| `") }
                .map { it.substringAfter("`").substringBefore("`") }
                .toSet()
        val actual =
            listOf(MutKtExtension::class.java, ReportsSpec::class.java)
                .flatMap { type -> type.declaredMethods.toList() }
                .filter { it.name.startsWith("get") && it.parameterCount == 0 }
                .map { it.name.removePrefix("get").replaceFirstChar(Char::lowercase) }
                .filter { it != "reports" }
                .toSet()

        assertTrue(actual.isNotEmpty())
        assertEquals(actual, documented)
    }

    private fun Project.evaluate() = (this as org.gradle.api.internal.project.ProjectInternal).evaluate()
}
