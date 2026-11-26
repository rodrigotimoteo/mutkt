package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.application.port.ClassFile
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class KotestPropertiesTest {
    @TempDir
    lateinit var dir: Path

    private val configScanOff = "-Dkotest.framework.classpath.scanning.config.disable=true"
    private val autoScanOff = "-Dkotest.framework.classpath.scanning.autoscan.disable=true"

    // One package per compilation, so the fake Kotest API and the project's classes share Kotest's.
    private val configs =
        compileKotlin(
            """
            package io.kotest.core.config

            abstract class AbstractProjectConfig

            abstract class BaseConfig : AbstractProjectConfig() {
                val big = 1L shl 40
                val ratio = 0.5
            }

            object ProjectConfig : BaseConfig()

            class OtherConfig : AbstractProjectConfig()

            class SomeTest
            """.trimIndent(),
        ).map { ClassFile(it.key, it.value) }

    private fun kotest(): List<Path> {
        val api = configs.filter { it.name == "io.kotest.core.config.AbstractProjectConfig" }
        return listOf(writeClasses(dir.resolve("kotest"), api))
    }

    private fun tests(vararg names: String): Path {
        val simple = names.map { "io.kotest.core.config.$it" }
        return writeClasses(dir.resolve("tests"), configs.filter { it.name in simple })
    }

    @Test
    fun `without Kotest on the classpath nothing is set`() {
        assertEquals(emptyList<String>(), kotestProperties(emptyList(), tests("SomeTest"), emptyList()))
    }

    @Test
    fun `without a project config both scans are turned off`() {
        assertEquals(listOf(configScanOff, autoScanOff), kotestProperties(kotest(), tests("SomeTest"), emptyList()))
    }

    @Test
    fun `a project config, also through an abstract base class, is named instead of scanned`() {
        assertEquals(
            listOf(configScanOff, "-Dkotest.framework.config.fqn=io.kotest.core.config.ProjectConfig", autoScanOff),
            kotestProperties(kotest(), tests("SomeTest", "BaseConfig", "ProjectConfig"), emptyList()),
        )
    }

    @Test
    fun `several project configs keep the config scan`() {
        assertEquals(
            listOf(autoScanOff),
            kotestProperties(kotest(), tests("BaseConfig", "ProjectConfig", "OtherConfig"), emptyList()),
        )
    }

    @Test
    fun `properties the build sets are not overridden`() {
        val own =
            listOf(
                "-Dkotest.framework.config.fqn=com.example.Config",
                "-Dkotest.framework.classpath.scanning.autoscan.disable",
            )
        assertEquals(emptyList<String>(), kotestProperties(kotest(), tests("ProjectConfig"), own))
        val scanOn = listOf("-Dkotest.framework.classpath.scanning.config.disable=false")
        assertEquals(listOf(autoScanOff), kotestProperties(kotest(), tests("ProjectConfig"), scanOn))
    }

    @Test
    fun `a project config in a class directory of the classpath is named`() {
        val chain = configs.filter { it.name.endsWith("ProjectConfig") || it.name.endsWith("BaseConfig") }
        val shared = writeClasses(dir.resolve("fixtures"), chain)
        assertEquals(
            listOf(configScanOff, "-Dkotest.framework.config.fqn=io.kotest.core.config.ProjectConfig", autoScanOff),
            kotestProperties(kotest() + listOf(shared), tests("SomeTest"), emptyList()),
        )
    }

    @Test
    fun `a dependency jar mentioning the config type keeps the config scan`() {
        val source = "package com.example\nobject SharedConfig : io.kotest.core.config.AbstractProjectConfig()"
        val jar = writeJar(dir.resolve("shared.jar"), compileKotlin(source, classpath = kotest()))
        assertEquals(listOf(autoScanOff), kotestProperties(kotest() + listOf(jar), tests("SomeTest"), emptyList()))
    }

    @Test
    fun `a dependency jar with an AutoScan class keeps the autoscan`() {
        val annotation = autoScan().filterKeys { it.endsWith("AutoScan") }.map { ClassFile(it.key, it.value) }
        val api = writeClasses(dir.resolve("annotation"), annotation)
        val source = "package com.example\n@io.kotest.core.annotation.AutoScan class JarListener"
        val jar = writeJar(dir.resolve("listener.jar"), compileKotlin(source, classpath = listOf(api)))
        assertEquals(listOf(configScanOff), kotestProperties(kotest() + listOf(jar), tests("SomeTest"), emptyList()))
    }

    @Test
    fun `Kotest's own jars are not searched`() {
        val own = writeJar(dir.resolve("kotest.jar"), configs.associate { it.name to it.bytes } + autoScan())
        assertEquals(listOf(configScanOff, autoScanOff), kotestProperties(listOf(own), tests("SomeTest"), emptyList()))
    }

    @Test
    fun `Kotest's runner jar is not searched`() {
        val source =
            "package io.kotest.runner.junit5\n" +
                "class Runner { val type = io.kotest.core.config.AbstractProjectConfig::class }"
        val runner = writeJar(dir.resolve("kotest-runner.jar"), compileKotlin(source, classpath = kotest()))
        assertEquals(
            listOf(configScanOff, autoScanOff),
            kotestProperties(kotest() + listOf(runner), tests("SomeTest"), emptyList()),
        )
    }

    private fun autoScan() =
        compileKotlin("package io.kotest.core.annotation\nannotation class AutoScan\n@AutoScan class Listener")

    @Test
    fun `an AutoScan test class keeps the autoscan`() {
        val listener = autoScan().filterKeys { it.endsWith("Listener") }.map { ClassFile(it.key, it.value) }
        val tests = writeClasses(dir.resolve("tests"), listener)
        assertEquals(listOf(configScanOff), kotestProperties(kotest(), tests, emptyList()))
    }
}
