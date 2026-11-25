package com.github.rodrigotimoteo.mutkt.junit

import com.github.rodrigotimoteo.mutkt.testing.buildClass
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.objectweb.asm.Opcodes.ICONST_1
import org.objectweb.asm.Opcodes.ICONST_2
import org.objectweb.asm.Opcodes.IRETURN
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MutantClassLoaderTest {
    private val testLoader = MutantClassLoaderTest::class.java.classLoader

    private fun answer(opcode: Int) = buildClass("Answer", "value") { visitInsn(opcode).also { visitInsn(IRETURN) } }

    private fun ClassLoader.invokeStatic(
        className: String,
        method: String,
    ) = loadClass(className).getMethod(method).invoke(null)

    @Test
    fun `mapped class is defined from the given bytes`() {
        MutantClassLoader(testLoader, mapOf("Answer" to answer(ICONST_2))).use {
            assertEquals(2, it.invokeStatic("Answer", "value"))
        }
    }

    @Test
    fun `mapped class is defined once`() {
        MutantClassLoader(testLoader, mapOf("Answer" to answer(ICONST_2))).use {
            assertSame(it.loadClass("Answer"), it.loadClass("Answer"))
        }
    }

    @Test
    fun `concurrent loads of a mapped class yield one Class instance`() {
        MutantClassLoader(testLoader, mapOf("Answer" to answer(ICONST_2))).use { loader ->
            val pool = Executors.newFixedThreadPool(8)
            try {
                val start = CountDownLatch(1)
                val loads = List(8) { pool.submit<Class<*>> { start.await().let { loader.loadClass("Answer") } } }
                start.countDown()
                assertEquals(1, loads.map { it.get(10, TimeUnit.SECONDS) }.toSet().size)
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun `class loaded by the parent stays unmutated`() {
        MutantClassLoader(testLoader, mapOf("Answer" to answer(ICONST_1))).use { original ->
            MutantClassLoader(original, mapOf("Answer" to answer(ICONST_2))).use { mutant ->
                assertEquals(1, original.invokeStatic("Answer", "value"))
                assertEquals(2, mutant.invokeStatic("Answer", "value"))
            }
        }
    }

    @Test
    fun `unmapped names delegate to the parent`() {
        MutantClassLoader(testLoader, emptyMap()).use {
            assertSame(String::class.java, it.loadClass("java.lang.String"))
            assertSame(MutantClassLoaderTest::class.java, it.loadClass(MutantClassLoaderTest::class.java.name))
        }
    }

    @Test
    fun `two loaders do not share static state`() {
        val classes = compileKotlin("object Counter { private var n = 0; @JvmStatic fun next(): Int = ++n }")
        MutantClassLoader(testLoader, classes).use { first ->
            MutantClassLoader(testLoader, classes).use { second ->
                assertNotSame(first.loadClass("Counter"), second.loadClass("Counter"))
                assertEquals(1, first.invokeStatic("Counter", "next"))
                assertEquals(2, first.invokeStatic("Counter", "next"))
                assertEquals(1, second.invokeStatic("Counter", "next"))
            }
        }
    }

    @Test
    fun `inner classes come from the map`() {
        val classes = compileKotlin("class Outer { class Inner; fun make(): Any = Inner() }")
        MutantClassLoader(testLoader, classes).use {
            val outer = it.loadClass("Outer")
            val inner = outer.getMethod("make").invoke(outer.getDeclaredConstructor().newInstance())
            assertEquals("Outer\$Inner", inner.javaClass.name)
            assertSame(it, inner.javaClass.classLoader)
        }
    }

    @Test
    fun `mapped classes cannot be loaded after close and close is idempotent`() {
        val loader = MutantClassLoader(testLoader, mapOf("Answer" to answer(ICONST_2)))
        loader.close()
        loader.close()
        assertThrows<IllegalStateException> { loader.loadClass("Answer") }
    }

    private fun parentWith(vararg files: Pair<String, ByteArray>): URLClassLoader {
        val root = Files.createTempDirectory("mutkt-parent")
        files.forEach { (name, bytes) ->
            Files.createDirectories(root.resolve(name).parent)
            Files.write(root.resolve(name), bytes)
        }
        return URLClassLoader(arrayOf(root.toUri().toURL()), null)
    }

    private fun java.net.URL.bytes() = openStream().use { it.readBytes() }

    @Test
    fun `class resource of a mapped class is its bytes, ahead of a parent copy`() {
        val bytes = answer(ICONST_2)
        parentWith("p/Answer.class" to byteArrayOf(1, 2, 3)).use { parent ->
            MutantClassLoader(parent, mapOf("p.Answer" to bytes)).use {
                assertArrayEquals(bytes, it.getResourceAsStream("p/Answer.class")!!.use { s -> s.readBytes() })
                assertArrayEquals(bytes, it.getResource("p/Answer.class")!!.bytes())
                assertEquals(bytes.size, it.getResource("p/Answer.class")!!.openConnection().contentLength)
                assertEquals(bytes.size.toLong(), it.getResource("p/Answer.class")!!.openConnection().contentLengthLong)
                assertNull(it.getResource("/p/Answer.class"))
                assertArrayEquals(
                    bytes,
                    it
                        .getResources("p/Answer.class")
                        .toList()
                        .single()
                        .bytes(),
                )
            }
        }
    }

    @Test
    fun `class resource lookups from the defined class agree with its bytes`() {
        val classes = compileKotlin("package p\nclass Foo")
        MutantClassLoader(testLoader, classes).use {
            val foo = it.loadClass("p.Foo")
            assertArrayEquals(classes.getValue("p.Foo"), foo.getResourceAsStream("Foo.class")!!.readBytes())
            assertArrayEquals(classes.getValue("p.Foo"), foo.getResourceAsStream("/p/Foo.class")!!.readBytes())
        }
    }

    @Test
    fun `other resources and unmapped class resources delegate to the parent`() {
        val parentBytes = byteArrayOf(9)
        parentWith("data.txt" to parentBytes, "Other.class" to parentBytes).use { parent ->
            MutantClassLoader(parent, mapOf("Answer" to answer(ICONST_2))).use {
                assertArrayEquals(parentBytes, it.getResourceAsStream("data.txt")!!.use { s -> s.readBytes() })
                assertArrayEquals(parentBytes, it.getResourceAsStream("Other.class")!!.use { s -> s.readBytes() })
                assertNull(it.getResource("Missing.class"))
            }
        }
    }

    @Test
    fun `unmapped names still delegate to the parent after close`() {
        val loader = MutantClassLoader(testLoader, mapOf("Answer" to answer(ICONST_2)))
        loader.close()
        assertSame(String::class.java, loader.loadClass("java.lang.String"))
    }
}
