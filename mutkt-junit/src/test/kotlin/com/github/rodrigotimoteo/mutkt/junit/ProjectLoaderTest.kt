package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.platform.launcher.Launcher
import org.objectweb.asm.Type
import org.opentest4j.AssertionFailedError
import java.nio.file.Files
import java.nio.file.Path

class ProjectLoaderTest {
    private val pluginLoader = ProjectLoaderTest::class.java.classLoader

    @TempDir
    lateinit var dir: Path

    private fun writeClasses(classes: Map<String, ByteArray>) =
        classes.forEach { (name, bytes) ->
            val file = dir.resolve(name.replace('.', '/') + ".class")
            Files.createDirectories(file.parent)
            Files.write(file, bytes)
        }

    @Test
    fun `project classes shadow plugin classes that are not launcher api`() {
        writeClasses(compileKotlin("package org.objectweb.asm\nclass Type { fun origin() = \"project\" }"))
        projectLoader(listOf(dir), pluginLoader, emptyMap(), emptyMap()).use {
            val type = it.loadClass("org.objectweb.asm.Type")
            assertNotSame(Type::class.java, type)
            assertEquals("project", type.getMethod("origin").invoke(type.getDeclaredConstructor().newInstance()))
        }
    }

    @Test
    fun `plugin stdlib is not visible to the project`() {
        projectLoader(emptyList(), pluginLoader, emptyMap(), emptyMap()).use {
            assertTrue(runCatching { it.loadClass("kotlin.Unit") }.exceptionOrNull() is ClassNotFoundException)
        }
    }

    @Test
    fun `launcher api and opentest4j are the plugin classes`() {
        projectLoader(emptyList(), pluginLoader, emptyMap(), emptyMap()).use {
            assertSame(Launcher::class.java, it.loadClass(Launcher::class.java.name))
            assertSame(AssertionFailedError::class.java, it.loadClass(AssertionFailedError::class.java.name))
        }
    }

    @Test
    fun `launcher api resources come from the plugin`() {
        val resource = "org/junit/platform/launcher/Launcher.class"
        projectLoader(emptyList(), pluginLoader, emptyMap(), emptyMap()).use {
            assertEquals(pluginLoader.getResource(resource), it.getResource(resource))
            assertEquals(pluginLoader.getResources(resource).toList(), it.getResources(resource).toList())
        }
    }

    @Test
    fun `close releases the loader and is idempotent`() {
        writeClasses(compileKotlin("class Early\nclass Late"))
        val loader = projectLoader(listOf(dir), pluginLoader, emptyMap(), emptyMap())
        loader.loadClass("Early")
        loader.close()
        loader.close()
        assertTrue(runCatching { loader.loadClass("Late") }.exceptionOrNull() is ClassNotFoundException)
    }

    @Test
    fun `jupiter api is not served from the plugin`() {
        projectLoader(emptyList(), pluginLoader, emptyMap(), emptyMap()).use {
            assertTrue(runCatching { it.loadClass(Test::class.java.name) }.exceptionOrNull() is ClassNotFoundException)
        }
    }

    @Test
    fun `engine service files of the plugin do not leak`() {
        projectLoader(emptyList(), pluginLoader, emptyMap(), emptyMap()).use {
            assertTrue(it.getResources(ENGINE_SERVICE).toList().isEmpty())
        }
    }

    @Test
    fun `engine service files of the project are found`() {
        val file = dir.resolve(ENGINE_SERVICE)
        Files.createDirectories(file.parent)
        Files.writeString(file, "com.example.Engine")
        projectLoader(listOf(dir), pluginLoader, emptyMap(), emptyMap()).use {
            assertEquals(listOf(file.toUri().toURL()), it.getResources(ENGINE_SERVICE).toList())
        }
    }

    @Test
    fun `launcher api siblings are served from the project`() {
        writeClasses(compileKotlin("package org.junit.platform.suite.api\nclass Marker"))
        projectLoader(listOf(dir), pluginLoader, emptyMap(), emptyMap()).use {
            assertSame(it, it.loadClass("org.junit.platform.suite.api.Marker").classLoader)
        }
    }

    private companion object {
        const val ENGINE_SERVICE = "META-INF/services/org.junit.platform.engine.TestEngine"
    }
}
