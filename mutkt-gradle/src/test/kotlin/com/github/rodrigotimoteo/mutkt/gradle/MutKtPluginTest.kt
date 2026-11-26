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
        assertEquals(60_000L, mutkt.timeoutMs.get())
        assertEquals(setOf("junit-jupiter"), mutkt.engines.get())
        assertTrue(mutkt.targetClasses.get().isEmpty())
        // The generated-class globs are Android only.
        assertTrue(mutkt.excludedClasses.get().isEmpty())
        assertTrue(mutkt.operators.get().isEmpty())
        assertFalse(mutkt.maxMutantsPerClass.isPresent)
        assertFalse(mutkt.failBelowScore.isPresent)
        assertEquals("debug", mutkt.variant.get())
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
    fun `fails without a Java, Kotlin JVM or Android plugin`() {
        val failure = assertThrows<GradleException> { project().evaluate() }
        val messages = generateSequence<Throwable>(failure) { it.cause }.map { it.message.orEmpty() }
        assertTrue(messages.any { "MutKt requires the Java, Kotlin JVM or Android plugin" in it })
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
        val actual = dslProperties()

        assertTrue(actual.isNotEmpty())
        assertEquals(actual, documented)
    }

    @Test
    fun `migration guide maps every old property to an existing one or to removed`() {
        val rows =
            File(System.getProperty("mutkt.dslDoc"))
                .resolveSibling("migration.md")
                .readLines()
                .filter { it.startsWith("| `") }
                .map { line -> line.split("|").map(String::trim) }
        val replacements = rows.map { it[2] }.filter { it != "removed" }
        val properties = dslProperties()

        assertEquals(LEGACY_PROPERTIES, rows.map { it[1].removeSurrounding("`") }.toSet())
        assertTrue(replacements.isNotEmpty())
        replacements.forEach { cell ->
            assertTrue(cell.startsWith("`"), "Replacement must be a property or 'removed': $cell")
            assertTrue(cell.substringAfter("`").substringBefore("`") in properties, cell)
        }
    }

    private fun dslProperties(): Set<String> =
        listOf(MutKtExtension::class.java, ReportsSpec::class.java)
            .flatMap { type -> type.declaredMethods.toList() }
            .filter { it.name.startsWith("get") && it.parameterCount == 0 }
            .map { it.name.removePrefix("get").replaceFirstChar(Char::lowercase) }
            .filter { it != "reports" }
            .toSet()

    private fun Project.evaluate() = (this as org.gradle.api.internal.project.ProjectInternal).evaluate()

    private companion object {
        // Properties of the 0.3.3 MutationPluginExtension that docs/migration.md must map.
        val LEGACY_PROPERTIES =
            setOf(
                "targetClasses",
                "testClasses",
                "classpath",
                "coverageExecFile",
                "autoRunJaCoCo",
                "enabledOperators",
                "timeoutMs",
                "mutantTimeoutMs",
                "maxParallelMutants",
                "outputDir",
                "failOnSurvived",
                "failOnMutationScoreThreshold",
                "failOnScoreThreshold",
                "maxMutationsPerClass",
                "excludedClasses",
                "excludedMethods",
                "excludeGeneratedClasses",
                "targetClassPatterns",
                "excludeClassPatterns",
                "targetPackages",
                "excludePackages",
                "targetTestPatterns",
                "excludeTestPatterns",
                "reportFormats",
                "showClassScores",
                "generateGraph",
                "ciMode",
                "verbose",
                "enableIncrementalAnalysis",
                "enableCache",
                "enableSubsumption",
                "enableWeakMutation",
                "enableInlinedFinally",
                "enableTestOrdering",
                "isAndroid",
                "androidPluginType",
                "androidVariant",
                "androidContext",
            )
    }
}
