package com.github.rodrigotimoteo.mutkt.gradle

import org.gradle.api.GradleException
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

// A support class would silently replace the main class of the same name in the child JVMs: a broken build.
internal fun checkNoSharedClasses(
    main: Set<File>,
    support: Set<File>,
) {
    val shared = classNames(main).intersect(classNames(support))
    if (shared.isNotEmpty()) {
        throw GradleException(
            "Classes defined both in main and in another source set on the test classpath: " +
                shared.sorted().joinToString(),
        )
    }
}

private fun classNames(roots: Set<File>): Set<String> =
    roots.filter(File::exists).flatMapTo(mutableSetOf(), ::classPaths)

// Class file paths under a class directory or in a jar. A missing root is an empty output directory.
private fun classPaths(root: File): List<String> =
    when {
        root.isDirectory ->
            root
                .walk()
                .filter { it.isFile && it.extension == "class" }
                .map { it.relativeTo(root).invariantSeparatorsPath }
                .toList()
        root.extension == "jar" -> ZipFile(root).use { zip -> stagedEntries(zip).map(ZipEntry::getName).toList() }
        else -> throw GradleException("MutKt reads classes from directories and jars only, not $root")
    }.filter { it.endsWith(".class") }

// Multi-release copies would reach the fork under names like META-INF.versions.11.demo.Calc; the base copy is enough.
private fun stagedEntries(zip: ZipFile) =
    zip.entries().asSequence().filterNot { it.isDirectory || it.name.startsWith("META-INF/versions/") }

// Several class directories (Kotlin and Java output) and, on Android, jars become one, because the fork reads a
// single root per side. A class in two roots would silently replace the other one, so that fails the run.
internal fun stage(
    entries: Set<File>,
    into: File,
): Path {
    val roots = entries.filter(File::exists)
    val origins = mutableMapOf<String, File>()
    roots.forEach { root ->
        classPaths(root).forEach { path ->
            origins.put(path, root)?.let { throw GradleException("Class $path is in both $it and $root") }
        }
    }
    into.deleteRecursively()
    into.mkdirs()
    roots.forEach { if (it.isDirectory) it.copyRecursively(into, overwrite = true) else unpack(it, into) }
    return into.toPath()
}

private fun unpack(
    jar: File,
    into: File,
) = ZipFile(jar).use { zip ->
    val root = into.toPath().normalize()
    stagedEntries(zip).forEach { entry ->
        val target = root.resolve(entry.name).normalize()
        if (!target.startsWith(root)) throw GradleException("Unsafe jar entry ${entry.name} in $jar")
        target.toFile().parentFile.mkdirs()
        zip.getInputStream(entry).use { input -> Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING) }
    }
}
