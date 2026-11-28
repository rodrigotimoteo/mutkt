package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.asm.sourceFileOf
import java.nio.file.Files
import java.nio.file.Path

/**
 * The `excludedClasses` entry, there by default, that skips classes compiled from generated sources; it matches no
 * class name itself, so replacing the list without it mutates them.
 */
internal const val GENERATED_SOURCES: String = "<generated>"

/**
 * Names of the [classes] compiled from `<package directory>/<SourceFile>` under [generatedRoot] (`build/generated`,
 * where KSP, kapt and AGP write) when no file of that name exists anywhere in [mainSources].
 */
internal fun generatedClasses(
    classes: List<ClassFile>,
    generatedRoot: Path,
    mainSources: List<Path>,
): Set<String> {
    val generated = filesByName(listOf(generatedRoot))
    // Any same-named main file exempts the class, even one no lookup could pick: when unsure, mutate.
    val main = filesByName(mainSources).keys
    return classes
        .filter { cls ->
            val file = sourceFileOf(cls.bytes) ?: return@filter false
            val directory = cls.name.substringBeforeLast('.', "").replace('.', '/')
            val relative = if (directory.isEmpty()) file else "$directory/$file"
            file !in main && generated[file].orEmpty().any { it == relative || it.endsWith("/$relative") }
        }.mapTo(mutableSetOf()) { it.name }
}

// Paths relative to their root, by file name. Generators write each file into its package directory under an output
// root of their own (`ksp/<variant>/kotlin`, `source/kapt/<variant>`), so a path that ends with that directory matches.
private fun filesByName(roots: List<Path>): Map<String, List<String>> =
    roots
        .filter(Files::isDirectory)
        .flatMap { root ->
            Files.walk(root).use { paths ->
                paths
                    .filter(Files::isRegularFile)
                    .map { root.relativize(it).joinToString("/") }
                    .toList()
            }
        }.groupBy { it.substringAfterLast('/') }
