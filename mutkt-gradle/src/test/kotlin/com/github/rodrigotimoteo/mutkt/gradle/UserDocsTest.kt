package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class UserDocsTest {
    private val readme = File(checkNotNull(System.getProperty("mutkt.readme")) { "mutkt.readme is not set" })
    private val pages =
        listOf(readme) + File(readme.parentFile, "docs").listFiles { file -> file.extension == "md" }.orEmpty()

    private fun findAll(pattern: Regex): Set<String> =
        pages.flatMap { page -> pattern.findAll(page.readText()).map { it.groupValues[1] }.toList() }.toSet()

    @Test
    fun `plugin ids in the docs apply MutKt`() {
        val ids = findAll(PLUGIN_ID)

        assertTrue(ids.isNotEmpty())
        ids.forEach { id ->
            val project = ProjectBuilder.builder().build()
            project.pluginManager.apply(id)
            assertTrue(project.plugins.hasPlugin(MutKtPlugin::class.java), id)
        }
    }

    @Test
    fun `MutKt tasks named in the docs exist`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("java")
        project.pluginManager.apply(MutKtPlugin::class.java)
        val tasks = findAll(MUTKT_TASK)

        assertTrue("mutkt" in tasks)
        tasks.forEach { assertNotNull(project.tasks.findByName(it), "./gradlew $it") }
    }

    @Test
    fun `dsl md lists every Android generated-class glob`() {
        val section =
            File(readme.parentFile, "docs/dsl.md")
                .readText()
                .substringAfter("### Android generated classes")
                .substringBefore("\n#")

        assertEquals(ANDROID_GENERATED_CLASSES.toSet(), ANDROID_GLOB.findAll(section).map { it.groupValues[1] }.toSet())
    }

    @Test
    fun `dsl md names the generated-sources entry of excludedClasses`() {
        val dsl = File(readme.parentFile, "docs/dsl.md").readText()

        assertTrue("`$GENERATED_SOURCES`" in dsl)
    }

    private companion object {
        val ANDROID_GLOB = Regex("`(\\*\\*\\.[^`]+)`")

        // The legacy id shares this prefix, so a stale id is caught too.
        val PLUGIN_ID = Regex("""id\("(io\.github\.rodrigotimoteo\.[^"]+)"\)""")

        // Repository tasks (`:mutkt-asm:test`, `-Pmutkt.version`) are skipped; the legacy `mutationTest` is caught.
        val MUTKT_TASK = Regex("""\./gradlew[^\n`]*?(?<![:/\w.-])(mut[\w-]*)""")
    }
}
