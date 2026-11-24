package com.github.rodrigotimoteo.mutkt.asm

import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.CHECKCAST
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

class MutateTest {
    private val classes =
        compileKotlin(
            """
            open class Animal { fun legs() = 4 }
            class Dog : Animal()
            class Cat : Animal()

            fun add(a: Int, b: Int) = a + b
            fun twice(a: Int, b: Int) = (a + b) + (a + b)

            fun branchy(a: Int, b: Int): Int {
                val label = when {
                    a > 10 -> "big"
                    a > 5 -> "mid"
                    else -> "small"
                }
                var total = 0
                for (i in 0 until b) total += if (i % 2 == 0) a + i else i
                return try { total + label.length } catch (e: IllegalStateException) { -1 }
            }

            fun pick(flag: Boolean, a: Int, b: Int): Int {
                val pet: Animal = if (flag) Dog() else Cat()
                return pet.legs() + a + b
            }
            """.trimIndent(),
        )
    private val file = classes.getValue("SourceKt")
    private val noLookup: Hierarchy = { null }

    private fun mutated(
        method: String,
        ordinal: Int,
        hierarchy: Hierarchy = noLookup,
    ) = discover(file, ToyOperator)
        .single { it.methodName == method && it.ordinal == ordinal }
        .let { mutate(file, ToyOperator, it, hierarchy) }

    private fun site(
        method: String,
        descriptor: String,
        ordinal: Int,
    ) = Found("SourceKt", method, descriptor, ordinal, null, "")

    private val pickSite = site("pick", "(ZII)I", 0)

    // The compiler casts both branches to Animal, which hides the merge; drop the casts so frames
    // must join Dog and Cat.
    private val uncast =
        parse(file)
            .also { node ->
                node.methods.filter { it.name == "pick" }.forEach { m ->
                    m.instructions.filterIsInstance<TypeInsnNode>().filter { it.opcode == CHECKCAST }.forEach(
                        m.instructions::remove,
                    )
                }
            }.toBytes()

    // Lets the verifier see project types without them being on the test classpath.
    private val projectLoader =
        object : ClassLoader(MutateTest::class.java.classLoader) {
            override fun findClass(name: String): Class<*> =
                classes[name]?.let { defineClass(name, it, 0, it.size) } ?: throw ClassNotFoundException(name)
        }

    private fun verifyLog(bytes: ByteArray) =
        StringWriter()
            .also {
                CheckClassAdapter.verify(
                    ClassReader(bytes),
                    projectLoader,
                    false,
                    PrintWriter(it),
                )
            }.toString()

    @Test
    fun `mutating changes behavior and leaves the input untouched`() {
        val before = file.copyOf()
        val result = mutated("add", 0)

        assertEquals(8, loadAndInvoke(classes, "SourceKt", "add", 5, 3))
        assertEquals(2, loadAndInvoke(classes + ("SourceKt" to result), "SourceKt", "add", 5, 3))
        assertTrue(before.contentEquals(file))
    }

    @Test
    fun `only the chosen ordinal changes`() {
        val second = classes + ("SourceKt" to mutated("twice", 1))

        assertEquals(16, loadAndInvoke(classes, "SourceKt", "twice", 5, 3))
        assertEquals(10, loadAndInvoke(second, "SourceKt", "twice", 5, 3))
    }

    @Test
    fun `a class that needs frames stays verifiable`() {
        val result = mutated("branchy", 2)

        assertEquals("", verifyLog(result))
        assertEquals(23, loadAndInvoke(classes, "SourceKt", "branchy", 7, 4))
        assertEquals(17, loadAndInvoke(classes + ("SourceKt" to result), "SourceKt", "branchy", 7, 4))
    }

    @Test
    fun `project types are resolved through the lookup`() {
        val asked = mutableListOf<String>()
        val lookup: Hierarchy = { type ->
            asked += type
            classes[type.replace('/', '.')]
        }

        val result = mutate(uncast, ToyOperator, pickSite, lookup)

        assertTrue(asked.containsAll(listOf("Dog", "Cat")), asked.toString())
        assertEquals("", verifyLog(result))
        assertEquals(11, loadAndInvoke(classes, "SourceKt", "pick", true, 5, 2))
        assertEquals(1, loadAndInvoke(classes + ("SourceKt" to result), "SourceKt", "pick", true, 5, 2))
    }

    @Test
    fun `unknown types fail fast instead of falling back to Object`() {
        val error = assertThrows<IllegalStateException> { mutate(uncast, ToyOperator, pickSite, noLookup) }

        assertTrue(error.message!!.contains("Dog") || error.message!!.contains("Cat"), error.message)
    }

    @Test
    fun `an ordinal out of range fails`() {
        val error = assertThrows<IllegalStateException> { mutate(file, ToyOperator, site("add", "(II)I", 1), noLookup) }

        assertTrue(error.message!!.contains("ordinal 1"), error.message)
    }

    @Test
    fun `a site for another class fails`() {
        val other = site("add", "(II)I", 0).copy(className = "Other")

        val error = assertThrows<IllegalStateException> { mutate(file, ToyOperator, other, noLookup) }

        assertTrue(error.message!!.contains("Other"), error.message)
    }

    @Test
    fun `an unknown method fails`() {
        val error = assertThrows<IllegalStateException> { mutate(file, ToyOperator, site("nope", "()V", 0), noLookup) }

        assertTrue(error.message!!.contains("nope()V"), error.message)
    }
}
