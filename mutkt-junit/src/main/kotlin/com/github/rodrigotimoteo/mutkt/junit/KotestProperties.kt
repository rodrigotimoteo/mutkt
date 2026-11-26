package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.junit.fork.readClassFiles
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

private const val KOTEST_MARKER = "io/kotest/core/config/AbstractProjectConfig.class"
private const val PROJECT_CONFIG = "io/kotest/core/config/AbstractProjectConfig"
private const val AUTO_SCAN = "Lio/kotest/core/annotation/AutoScan;"
private const val CONFIG_SCAN_OFF = "kotest.framework.classpath.scanning.config.disable"
private const val CONFIG_CLASS = "kotest.framework.config.fqn"
private const val AUTO_SCAN_OFF = "kotest.framework.classpath.scanning.autoscan.disable"

// Any Kotest module (core, engine, runner, extensions) mentions its own config type without holding a user config.
private const val KOTEST_PACKAGE = "io/kotest/"

// Kotest scans the whole classpath for its project config and @AutoScan extensions in every run (each has a fresh
// class loader), which on large classpaths outlasts the prepare limit. The project config of the test classes and of
// the class directories is named instead. A scan stays on when it could find what cannot be named (several configs,
// a dependency jar class that mentions the config type or @AutoScan), and a property the build sets itself is kept.
internal fun kotestProperties(
    classpath: List<Path>,
    testClassDir: Path,
    jvmArgs: List<String>,
): List<String> {
    if (classpath.none { it.holds(KOTEST_MARKER) }) return emptyList()

    fun setByBuild(key: String) = jvmArgs.any { it == "-D$key" || it.startsWith("-D$key=") }
    val local = readClassFiles(testClassDir) + classpath.filter(Files::isDirectory).flatMap(::readClassFiles)
    val inJars = classpath.filter(::isJar).flatMapTo(mutableSetOf(), ::kotestMentions)
    val configs = projectConfigs(local.associate { it.name to classHeader(it.bytes) })
    val unnamed = configs.size > 1 || PROJECT_CONFIG in inJars
    val config =
        if (unnamed || setByBuild(CONFIG_SCAN_OFF) || setByBuild(CONFIG_CLASS)) {
            emptyList()
        } else {
            listOf(CONFIG_SCAN_OFF to "true") + configs.map { CONFIG_CLASS to it }
        }
    val annotated = AUTO_SCAN in inJars || local.any { AUTO_SCAN in it.text() }
    val autoScan = if (annotated || setByBuild(AUTO_SCAN_OFF)) emptyList() else listOf(AUTO_SCAN_OFF to "true")
    return (config + autoScan).map { (key, value) -> "-D$key=$value" }
}

// Concrete classes whose superclass chain, through the other given classes, reaches AbstractProjectConfig.
private fun projectConfigs(headers: Map<String, ClassHeader>): List<String> {
    val byInternalName = headers.mapKeys { it.key.replace('.', '/') }

    fun isConfig(superName: String?): Boolean =
        superName == PROJECT_CONFIG || (superName != null && isConfig(byInternalName[superName]?.superName))

    val concrete = headers.filterValues { !Modifier.isAbstract(it.access) && isConfig(it.superName) }
    return concrete.keys.sorted()
}

private fun isJar(entry: Path) = Files.isRegularFile(entry) && entry.toString().endsWith(".jar")

// Which of the config type and @AutoScan the classes of a jar mention. A mention is not proof of a subclass or an
// extension, but it is enough to keep the scan. Kotest's own jars mention both and are skipped.
private fun kotestMentions(jar: Path): Set<String> =
    ZipFile(jar.toFile()).use { zip ->
        val entries = zip.entries().toList()
        if (entries.any { it.name.startsWith(KOTEST_PACKAGE) }) return emptySet()
        entries
            .filter { it.name.endsWith(".class") }
            .flatMapTo(mutableSetOf()) { entry ->
                val text = String(zip.getInputStream(entry).use { it.readBytes() }, Charsets.ISO_8859_1)
                listOf(PROJECT_CONFIG, AUTO_SCAN).filter(text::contains)
            }
    }

private fun ClassFile.text() = String(bytes, Charsets.ISO_8859_1)
