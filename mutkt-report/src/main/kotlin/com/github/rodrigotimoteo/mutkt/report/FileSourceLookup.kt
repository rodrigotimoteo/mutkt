package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.application.port.SourceLookup
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Reads source lines from [roots]; the first root holding the outermost class's `.kt` or `.java` file wins. */
public class FileSourceLookup(
    private val roots: List<Path>,
) : SourceLookup {
    override fun lineText(
        className: String,
        line: Int,
    ): String? {
        val relative = className.substringBefore('$').replace('.', '/')
        val file =
            roots
                .map { it.toAbsolutePath().normalize() }
                .flatMap { root -> EXTENSIONS.map { root to root.resolve("$relative.$it").normalize() } }
                // A class name must not escape its root (e.g. an absolute name).
                .firstOrNull { (root, path) -> path.startsWith(root) && Files.isRegularFile(path) }
                ?.second
        return file?.takeIf { line >= 1 }?.let { readLine(it, line) }
    }

    // InputStreamReader replaces malformed bytes instead of throwing, unlike Files.lines.
    private fun readLine(
        file: Path,
        line: Int,
    ): String? =
        Files.newInputStream(file).reader(StandardCharsets.UTF_8).buffered().use { reader ->
            reader.lineSequence().drop(line - 1).firstOrNull()
        }

    private companion object {
        val EXTENSIONS = listOf("kt", "java")
    }
}
