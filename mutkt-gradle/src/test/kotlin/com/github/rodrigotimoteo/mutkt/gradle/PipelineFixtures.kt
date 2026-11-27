package com.github.rodrigotimoteo.mutkt.gradle

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

// Compiler jars (kctfork is the jar named core-<version>) serve compileKotlin only, not the code under test.
private val COMPILER_JARS = Regex("kotlin-compiler-embeddable|^core-\\d")

// The test JVM's jars, as the Gradle task will pass them. Directories are left out on purpose: they hold this module's
// own classes and the other modules' build output, while the fixture exists only as bytes.
internal fun runtimeJars(): List<Path> =
    System
        .getProperty("java.class.path")
        .split(File.pathSeparator)
        .map(Paths::get)
        .filter(Files::isRegularFile)
        .filterNot { COMPILER_JARS.containsMatchIn(it.fileName.toString()) }

// Dependency types (Kotlin stdlib, Jupiter) for frame computation come from the test JVM's own classpath.
internal fun systemClass(internalName: String): ByteArray? =
    ClassLoader.getSystemResourceAsStream("$internalName.class")?.use { it.readBytes() }
