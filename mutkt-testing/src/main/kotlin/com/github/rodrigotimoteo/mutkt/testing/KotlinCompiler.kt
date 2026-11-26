package com.github.rodrigotimoteo.mutkt.testing

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.nio.file.Files
import java.nio.file.Path

private fun binaryName(
    root: Path,
    classFile: Path,
) = root
    .relativize(classFile)
    .toString()
    .removeSuffix(".class")
    .replace('/', '.')

/**
 * Compiles [source] (as file [fileName]) with the real Kotlin compiler; returns class bytes by binary name.
 * With [inheritClassPath] the caller's classpath is visible to the source (e.g. Jupiter annotations);
 * [kotlincArguments] go to kotlinc as written (e.g. `-Xjvm-default=all`).
 */
@OptIn(ExperimentalCompilerApi::class)
fun compileKotlin(
    source: String,
    inheritClassPath: Boolean = false,
    fileName: String = "Source.kt",
    kotlincArguments: List<String> = emptyList(),
): Map<String, ByteArray> {
    val workDir = Files.createTempDirectory("mutkt-kotlin")
    try {
        val result =
            KotlinCompilation()
                .apply {
                    workingDir = workDir.toFile()
                    sources = listOf(SourceFile.kotlin(fileName, source))
                    jvmTarget = "17"
                    verbose = false
                    this.inheritClassPath = inheritClassPath
                    this.kotlincArguments = kotlincArguments
                }.compile()
        check(result.exitCode == KotlinCompilation.ExitCode.OK) { "Kotlin compilation failed: ${result.messages}" }
        val root = result.outputDirectory.toPath()
        return Files.walk(root).use { paths ->
            paths
                .filter { it.toString().endsWith(".class") }
                .toList()
                .associate { binaryName(root, it) to Files.readAllBytes(it) }
        }
    } finally {
        workDir.toFile().deleteRecursively()
    }
}
