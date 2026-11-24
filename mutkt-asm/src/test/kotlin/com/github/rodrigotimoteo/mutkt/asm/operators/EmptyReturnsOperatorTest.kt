package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

private val SOURCE =
    """
    fun list(): List<Int> = listOf(1)
    fun mutableList(): MutableList<Int> = mutableListOf(1)
    fun set(): Set<Int> = setOf(1)
    fun map(): Map<Int, Int> = mapOf(1 to 1)
    fun mutableMap(): MutableMap<Int, Int> = mutableMapOf(1 to 1)
    fun collection(): Collection<Int> = listOf(1)
    fun ints(): IntArray = intArrayOf(1)
    fun longs(): LongArray = longArrayOf(1L)
    fun strings(): Array<String> = arrayOf("a")
    fun matrix(): Array<IntArray> = arrayOf(intArrayOf(1))
    fun cube(): Array<Array<String>> = arrayOf(arrayOf("a"))

    fun emptyL(): List<Int> = emptyList()
    fun emptyS(): Set<Int> = emptySet()
    fun emptyM(): Map<Int, Int> = emptyMap()
    fun emptyI(): IntArray = intArrayOf()
    fun emptyStrings(): Array<String> = arrayOf()

    fun str(): String = "a"
    fun arrayList(): ArrayList<Int> = arrayListOf(1)
    fun hashMap(): HashMap<Int, Int> = hashMapOf(1 to 1)
    fun count(): Int = 1
    fun nothing(): List<Int>? = null
    fun unit() { println() }
    suspend fun suspending(): List<Int> = listOf(1)

    fun over(a: Int): List<Int> = listOf(a)
    fun over(a: String): List<String> = listOf(a)
    fun two(a: Int): List<Int> {
        if (a < 0) return listOf(-1)
        return listOf(1)
    }
    fun pick(a: Boolean): List<Int> = if (a) emptyList() else listOf(1)
    fun callOverInt(): List<Int> = over(1)
    fun callOverString(): List<String> = over("s")
    fun callItems(): List<String> = Holder().items()
    class Holder { fun items(): List<String> = listOf("x") }
    """.trimIndent()

class EmptyReturnsOperatorTest {
    private val classes = compileKotlin(SOURCE)
    private val file = classes.getValue("SourceKt")
    private val noLookup: Hierarchy = { null }

    private fun sites(method: String) = discover(file, EmptyReturnsOperator).filter { it.methodName == method }

    private fun mutant(
        method: String,
        descriptor: String? = null,
        ordinal: Int = 0,
    ): Map<String, ByteArray> {
        val found =
            sites(
                method,
            ).single { it.ordinal == ordinal && (descriptor == null || it.descriptor == descriptor) }
        return classes + ("SourceKt" to mutate(file, EmptyReturnsOperator, found, noLookup))
    }

    private fun verify(bytes: ByteArray) =
        StringWriter().also { CheckClassAdapter.verify(ClassReader(bytes), false, PrintWriter(it)) }.toString()

    private fun size(value: Any?): Int =
        when (value) {
            is Collection<*> -> value.size
            is Map<*, *> -> value.size
            else ->
                java.lang.reflect.Array
                    .getLength(value)
        }

    private fun sizeOf(
        loaded: Map<String, ByteArray>,
        method: String,
    ) = size(loadAndInvoke(loaded, "SourceKt", method))

    @ParameterizedTest
    @ValueSource(strings = ["list", "mutableList", "set", "map", "mutableMap", "collection"])
    fun `collection returns become empty`(method: String) = becomesEmpty(method)

    @ParameterizedTest
    @ValueSource(strings = ["ints", "longs", "strings", "matrix", "cube"])
    fun `array returns become empty`(method: String) = becomesEmpty(method)

    private fun becomesEmpty(method: String) {
        val changed = mutant(method)

        assertEquals(1, sizeOf(classes, method), "$method original")
        assertEquals(0, sizeOf(changed, method), "$method mutant")
        assertEquals("areturn -> empty", sites(method).single().description)
        assertEquals("", verify(changed.getValue("SourceKt")))
    }

    @Test
    fun `the empty array keeps the component type`() {
        val types =
            listOf("ints", "longs", "strings", "matrix", "cube").map {
                loadAndInvoke(mutant(it), "SourceKt", it)!!.javaClass.simpleName
            }

        assertEquals(listOf("int[]", "long[]", "String[]", "int[][]", "String[][]"), types)
    }

    @Test
    fun `the empty collection has the returned kind`() {
        assertTrue(loadAndInvoke(mutant("list"), "SourceKt", "list") is List<*>)
        assertTrue(loadAndInvoke(mutant("set"), "SourceKt", "set") is Set<*>)
        assertTrue(loadAndInvoke(mutant("map"), "SourceKt", "map") is Map<*, *>)
        assertTrue(loadAndInvoke(mutant("collection"), "SourceKt", "collection") is Collection<*>)
    }

    @ParameterizedTest
    @ValueSource(strings = ["emptyL", "emptyS", "emptyM", "emptyI", "emptyStrings"])
    fun `returning an already empty value is not a site`(method: String) {
        assertEquals(emptyList<Any>(), sites(method))
    }

    @ParameterizedTest
    @ValueSource(strings = ["str", "arrayList", "hashMap", "count", "unit", "suspending"])
    fun `other return types have no site`(method: String) {
        assertEquals(emptyList<Any>(), sites(method))
    }

    @Test
    fun `overloads are separate sites told apart by descriptor`() {
        assertEquals(
            setOf("(I)Ljava/util/List;", "(Ljava/lang/String;)Ljava/util/List;"),
            sites("over").map { it.descriptor }.toSet(),
        )
        val changed = mutant("over", "(I)Ljava/util/List;")

        assertEquals(
            listOf(listOf(1), listOf("s")),
            listOf("callOverInt", "callOverString").map { loadAndInvoke(classes, "SourceKt", it) },
        )
        assertEquals(
            listOf(emptyList<Int>(), listOf("s")),
            listOf("callOverInt", "callOverString").map { loadAndInvoke(changed, "SourceKt", it) },
        )
    }

    @Test
    fun `each return of a method is its own ordinal`() {
        assertEquals(listOf(0, 1), sites("two").map { it.ordinal })
        assertEquals(
            listOf(emptyList<Int>(), listOf(1)),
            listOf(-1, 1).map { loadAndInvoke(mutant("two", ordinal = 0), "SourceKt", "two", it) },
        )
        assertEquals(
            listOf(listOf(-1), emptyList<Int>()),
            listOf(-1, 1).map { loadAndInvoke(mutant("two", ordinal = 1), "SourceKt", "two", it) },
        )
    }

    @Test
    fun `a null return of a collection type becomes empty`() {
        assertEquals(emptyList<Int>(), loadAndInvoke(mutant("nothing"), "SourceKt", "nothing"))
    }

    @Test
    fun `a branch that merges with an empty arm is still a site`() {
        assertEquals(1, sites("pick").size)
        assertEquals(
            listOf(emptyList<Int>(), emptyList<Int>()),
            listOf(true, false).map { loadAndInvoke(mutant("pick"), "SourceKt", "pick", it) },
        )
    }

    @Test
    fun `an instance method mutant is seen as empty by its caller`() {
        val holder = classes.getValue("Holder")
        val found = discover(holder, EmptyReturnsOperator).single { it.methodName == "items" }
        val changed = mutate(holder, EmptyReturnsOperator, found, noLookup)

        assertEquals(listOf("x"), loadAndInvoke(classes, "SourceKt", "callItems"))
        assertEquals(emptyList<String>(), loadAndInvoke(classes + ("Holder" to changed), "SourceKt", "callItems"))
        assertEquals("", verify(changed))
    }
}
