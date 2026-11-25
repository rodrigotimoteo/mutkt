package com.github.rodrigotimoteo.mutkt.gradle

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.application.port.ClassSource
import java.nio.file.Files
import java.nio.file.Path

// module-info and META-INF/versions copies are not classes the tests load by name.
private fun Path.isAnalysable(): Boolean =
    toString().endsWith(".class") && fileName.toString() != "module-info.class" && getName(0).toString() != "META-INF"

/** Reads the class files under [root] (not module-info or META-INF); the binary name is the relative path with dots. */
internal class DirectoryClassSource(
    private val root: Path,
) : ClassSource {
    override fun classes(): List<ClassFile> =
        Files.walk(root).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && root.relativize(it).isAnalysable() }
                .map { ClassFile(root.relativize(it).joinToString(".").removeSuffix(".class"), Files.readAllBytes(it)) }
                .toList()
        }
}
