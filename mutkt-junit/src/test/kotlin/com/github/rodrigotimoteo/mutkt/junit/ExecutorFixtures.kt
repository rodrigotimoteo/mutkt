package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.domain.Mutant
import com.github.rodrigotimoteo.mutkt.domain.MutantId
import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds

internal val deadline = 10.seconds

internal const val CALC = "class Calc { fun add(a: Int, b: Int) = a + b }"
internal const val CALC_MUTANT = "class Calc { fun add(a: Int, b: Int) = a - b }"

private val operator = OperatorId("math")

internal fun mutantOf(className: String = "Calc") =
    Mutant(MutantId(operator, className, "add", "(II)I", 0), operator, className, "add", "(II)I", 1, "d")

// The test runtime classpath minus class directories, as the Gradle task will pass it.
internal fun runtimeJars(): List<Path> =
    System
        .getProperty("java.class.path")
        .split(File.pathSeparator)
        .map(Paths::get)
        .filter(Files::isRegularFile)

internal fun mutantBytes(
    source: String = CALC_MUTANT,
    name: String = "Calc",
) = compileKotlin(source)[name] ?: error("No class $name")

/** Compiles [source] with Jupiter visible and splits the output: classes named in [production] versus tests. */
internal fun splitClasses(
    source: String,
    production: Set<String> = setOf("Calc"),
): Pair<List<ClassFile>, List<ClassFile>> =
    compileKotlin(source, inheritClassPath = true)
        .map { ClassFile(it.key, it.value) }
        .partition { it.name in production }

internal fun executorFor(
    source: String,
    production: Set<String> = setOf("Calc"),
): JUnitTestExecutor {
    val (project, tests) = splitClasses(source, production)
    return JUnitTestExecutor(project, tests, runtimeJars())
}

internal const val IMPORTS =
    "import org.junit.jupiter.api.*\nimport org.junit.jupiter.api.Assertions.*\n"
