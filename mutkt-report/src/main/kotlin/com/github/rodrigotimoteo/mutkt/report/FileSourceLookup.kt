package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.application.port.SourceLookup
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads source lines from [roots]: the first root holding `<package directory>/<source file>` wins. Kotlin files
 * may sit outside their package directory, so failing that the file is found only when exactly one file in all
 * roots has that name; zero or several matches find nothing.
 */
public class FileSourceLookup(
    private val roots: List<Path>,
) : SourceLookup {
    override fun lineText(
        className: String,
        sourceFile: String?,
        line: Int,
    ): String? {
        if (line < 1) return null
        return locate(className, sourceFile)?.let { readLine(it, line) }
    }

    override fun sourceText(
        className: String,
        sourceFile: String?,
    ): String? =
        locate(className, sourceFile)?.let { file ->
            Files.newInputStream(file).reader(StandardCharsets.UTF_8).use { it.readLines().joinToString("\n") }
        }

    /** The file [sourceText] reads, or null when it is not found. */
    public fun sourcePath(
        className: String,
        sourceFile: String?,
    ): Path? = locate(className, sourceFile)

    private fun locate(
        className: String,
        sourceFile: String?,
    ): Path? {
        if (sourceFile == null) return null
        val directory = className.substringBeforeLast('.', "").replace('.', '/')
        return roots
            .map { it.toAbsolutePath().normalize() }
            .map { root -> root to root.resolve(directory).resolve(sourceFile).normalize() }
            // Neither the class name nor the file name may escape its root.
            .firstOrNull { (root, path) -> path.startsWith(root) && Files.isRegularFile(path) }
            ?.second ?: byName[sourceFile]?.singleOrNull()
    }

    private val byName: Map<String, List<Path>> by lazy {
        roots
            .map { it.toAbsolutePath().normalize() }
            .filter(Files::isDirectory)
            .flatMap { root -> Files.walk(root).use { paths -> paths.filter(Files::isRegularFile).toList() } }
            .distinct()
            .groupBy { it.fileName.toString() }
    }

    // InputStreamReader replaces malformed bytes instead of throwing, unlike Files.lines.
    private fun readLine(
        file: Path,
        line: Int,
    ): String? =
        Files.newInputStream(file).reader(StandardCharsets.UTF_8).buffered().use { reader ->
            reader.lineSequence().drop(line - 1).firstOrNull()
        }
}
