package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GeneratedSourcesTest {
    @TempDir
    lateinit var root: Path

    private val generated get() = root.resolve("build/generated")
    private val main get() = root.resolve("src/main/kotlin")

    private val repo = "package demo\n\nclass Repo {\n    fun count() = 2\n}\n"
    private val impl = "package demo\n\nclass Repo_Impl {\n    fun count() = 1\n\n    class Holder\n}\n"

    private val classes =
        (
            compileKotlin(repo, fileName = "Repo.kt") +
                compileKotlin(impl, fileName = "Repo_Impl.kt")
        ).map { ClassFile(it.key, it.value) }

    private fun write(path: Path) {
        Files.createDirectories(path.parent)
        Files.writeString(path, "// source\n")
    }

    @Test
    fun `classes compiled from a file under the generated root are generated, nested ones included`() {
        write(main.resolve("demo/Repo.kt"))
        write(generated.resolve("ksp/main/kotlin/demo/Repo_Impl.kt"))

        assertEquals(
            setOf("demo.Repo_Impl", "demo.Repo_Impl\$Holder"),
            generatedClasses(classes, generated, listOf(main)),
        )
    }

    @Test
    fun `a file that also resolves in the main sources is not generated`() {
        write(main.resolve("demo/Repo.kt"))
        write(main.resolve("demo/Repo_Impl.kt"))
        write(generated.resolve("source/kapt/main/demo/Repo_Impl.kt"))

        assertEquals(emptySet<String>(), generatedClasses(classes, generated, listOf(main)))
    }

    @Test
    fun `a same-named main file is enough to mutate the class, even when no lookup could pick one`() {
        write(main.resolve("a/Repo.kt"))
        write(main.resolve("b/Repo.kt"))
        write(generated.resolve("ksp/main/kotlin/demo/Repo.kt"))

        assertEquals(emptySet<String>(), generatedClasses(classes, generated, listOf(main)))
    }

    @Test
    fun `a generated file of the same name in another package does not count`() {
        write(generated.resolve("ksp/main/kotlin/other/Repo_Impl.kt"))

        assertEquals(emptySet<String>(), generatedClasses(classes, generated, listOf(main)))
    }

    @Test
    fun `no generated directory means no generated classes`() {
        assertEquals(emptySet<String>(), generatedClasses(classes, generated, listOf(main)))
    }
}
