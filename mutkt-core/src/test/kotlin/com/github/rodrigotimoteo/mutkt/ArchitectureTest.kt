package com.github.rodrigotimoteo.mutkt

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

private const val ROOT = "com.github.rodrigotimoteo.mutkt"
private const val MAX_MAIN_LINES = 300
private val STATE_TYPES = Regex("(?i)mutable|atomic|arraylist|hashmap|hashset")

// Allowed project dependencies per module, from the module table in AGENTS.md. mutkt-gradle wires everything.
private val ALLOWED_PROJECTS =
    mapOf(
        "mutkt-annotations" to emptySet(),
        "mutkt-core" to emptySet(),
        "mutkt-asm" to setOf("mutkt-core", "mutkt-annotations"),
        "mutkt-junit" to setOf("mutkt-core"),
        "mutkt-report" to setOf("mutkt-core"),
        "mutkt-testing" to setOf("mutkt-core"),
    )

private val ADAPTERS = listOf("asm", "junit", "report", "gradle")

// Forms the project(":x") regex cannot see; banned so it cannot be bypassed silently.
private val UNCHECKED_PROJECT_FORMS = Regex("""\bprojects\.|project\(\s*path\s*=""")

// Rules are proven by planted violations (see ROD-77) and are vacuous until module sources land.
// Imports are checked, not fully-qualified references written without an import.
class ArchitectureTest {
    private val root =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }

    // Konsist reports build output and other source sets too, so select main sources by path.
    private val mainFiles =
        Konsist
            .scopeFromProject()
            .files
            .filter { "/src/main/" in it.path && "/build/" !in it.path }

    private fun sources(
        module: String,
        pkg: String? = null,
    ) = mainFiles.filter {
        "/$module/src/main/" in it.path && (pkg == null || "/mutkt/$pkg/" in it.path)
    }

    private fun importViolations(
        files: List<KoFileDeclaration>,
        disallowed: (String) -> Boolean,
    ) = files.flatMap { file -> file.imports.filter { disallowed(it.name) }.map { "${file.path}: ${it.name}" } }

    private fun assertNoViolations(
        violations: List<String>,
        message: String,
    ) = assertTrue(violations.isEmpty(), "$message:\n${violations.joinToString("\n")}")

    private fun assertImportsOnly(
        files: List<KoFileDeclaration>,
        vararg allowed: String,
    ) = assertNoViolations(
        importViolations(files) { name -> allowed.none { name.startsWith(it) } },
        "Disallowed imports",
    )

    @Test
    fun `domain imports only kotlin and domain`() =
        assertImportsOnly(sources("mutkt-core", "domain"), "kotlin.", "$ROOT.domain.")

    @Test
    fun `application imports only kotlin, domain and application`() =
        assertImportsOnly(sources("mutkt-core", "application"), "kotlin.", "$ROOT.domain.", "$ROOT.application.")

    @Test
    fun `core imports no third-party code`() =
        assertImportsOnly(sources("mutkt-core"), "kotlin.", "java.", "$ROOT.domain.", "$ROOT.application.")

    @Test
    fun `asm, junit and report never import other adapters`() =
        listOf("asm", "junit", "report").forEach { adapter ->
            val others = (ADAPTERS - adapter).map { "$ROOT.$it." }
            val violations = importViolations(sources("mutkt-$adapter")) { name -> others.any { name.startsWith(it) } }
            assertNoViolations(violations, "mutkt-$adapter imports another adapter")
        }

    @Test
    fun `modules depend only on the projects the architecture allows`() {
        val violations =
            ALLOWED_PROJECTS.flatMap { (module, allowed) ->
                val text = File(root, "$module/build.gradle.kts").readText()
                val declared =
                    Regex("""project\(":([^"]+)"\)""")
                        .findAll(text)
                        .map { it.groupValues[1] }
                        .filter { it !in allowed }
                        .map { "$module -> $it" }
                val unchecked = UNCHECKED_PROJECT_FORMS.findAll(text).map { "$module uses ${it.value}" }
                (declared + unchecked).toList()
            }
        assertTrue(violations.isEmpty(), "Disallowed project dependencies: $violations")
    }

    @Test
    fun `main code declares no global mutable state`() {
        val isStateful = { property: KoPropertyDeclaration ->
            property.isVar || STATE_TYPES.containsMatchIn(property.text)
        }
        val violations =
            mainFiles.flatMap { file ->
                val owners =
                    file.objects(includeNested = true).map { it.name to it.properties() } +
                        ("<top-level>" to file.properties(includeNested = false))
                owners.flatMap { (owner, properties) ->
                    properties.filter(isStateful).map { "${file.path}: $owner.${it.name}" }
                }
            }
        assertNoViolations(violations, "Global mutable state")
    }

    @Test
    fun `no main file exceeds the line limit`() {
        val violations = mainFiles.filter { File(it.path).readLines().size > MAX_MAIN_LINES }.map { it.path }
        assertTrue(violations.isEmpty(), "Main files over $MAX_MAIN_LINES lines: $violations")
    }
}
