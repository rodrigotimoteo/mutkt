package com.github.rodrigotimoteo.mutkt.junit.fork

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import java.nio.file.Files
import java.nio.file.Path

// Parsed from a key=value file (repeatable keys) because the classpath can exceed the OS command-line limit.
internal data class ForkStartup(
    val projectClassDir: Path,
    val testClassDir: Path,
    val classpath: List<Path>,
    val engines: List<String>,
    val includeTags: List<String> = emptyList(),
    val excludeTags: List<String> = emptyList(),
    val codeSources: Map<String, Path> = emptyMap(),
) {
    companion object {
        fun parse(file: Path): ForkStartup {
            val entries =
                Files
                    .readAllLines(file)
                    .filter { it.isNotBlank() }
                    .map { line ->
                        require('=' in line) { "Malformed startup line: $line" }
                        line.substringBefore('=') to line.substringAfter('=')
                    }.groupBy({ it.first }, { it.second })
            val unknown = entries.keys - KEYS
            require(unknown.isEmpty()) { "Unknown startup keys: $unknown" }
            val engines = entries["engine"].orEmpty()
            require(engines.isNotEmpty()) { "Startup file needs at least one engine" }
            return ForkStartup(
                Path.of(entries.single("projectClassDir")),
                Path.of(entries.single("testClassDir")),
                entries["classpath"].orEmpty().map(Path::of),
                engines,
                entries["includeTag"].orEmpty(),
                entries["excludeTag"].orEmpty(),
                // A binary name has no '=', so the first one ends it and the path may hold more.
                entries["codeSource"].orEmpty().associate { entry ->
                    entry.substringBefore('=') to Path.of(entry.substringAfter('='))
                },
            )
        }

        private val KEYS =
            setOf("projectClassDir", "testClassDir", "classpath", "engine", "includeTag", "excludeTag", "codeSource")

        private fun Map<String, List<String>>.single(key: String): String =
            requireNotNull(this[key]?.singleOrNull()) { "Startup file needs exactly one $key" }
    }
}

internal fun readClassFiles(root: Path): List<ClassFile> =
    Files.walk(root).use { paths ->
        paths
            .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".class") }
            .map {
                ClassFile(
                    root.relativize(it).joinToString(".") { part -> part.toString() }.removeSuffix(".class"),
                    Files.readAllBytes(it),
                )
            }.toList()
    }
