package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import java.nio.file.Files
import java.nio.file.Path

/** Writes [classes] as .class files under [target], the layout a project class directory has. */
internal fun writeClasses(
    target: Path,
    classes: List<ClassFile>,
): Path {
    classes.forEach {
        val file = target.resolve(it.name.replace('.', '/') + ".class")
        Files.createDirectories(file.parent)
        Files.write(file, it.bytes)
    }
    return target
}
