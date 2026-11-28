package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.IsolatedClasses
import com.github.rodrigotimoteo.mutkt.testing.buildClass
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.ILOAD
import org.objectweb.asm.Opcodes.IMUL
import org.objectweb.asm.Opcodes.IRETURN
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

// No string templates: they would need escaping inside this raw string.
private val SOURCE =
    """
    import kotlin.coroutines.Continuation
    import kotlin.coroutines.EmptyCoroutineContext
    import kotlin.coroutines.startCoroutine

    var hits = 0

    fun touch() { hits += 1 }
    fun touched(): Int { touch(); return hits }
    fun count(a: Int) = a + 1
    fun wide(a: Long, b: Double): Long = a + b.toLong()
    fun ratio(a: Double, b: Float): Double = a / b
    fun half(a: Float) = a / 2f
    fun big(a: Int) = a > 10
    fun letter(a: Int) = 'a' + a
    fun name(a: Int) = "n" + a
    fun names(a: Int): List<String> = listOf("n" + a)
    fun keys(a: Int): Map<String, Int> = mapOf("k" to a)
    fun unique(a: Int): Set<Int> = setOf(a)
    fun all(a: Int): Collection<Int> = listOf(a)
    fun codes(a: Int): IntArray = intArrayOf(a)
    fun labels(a: Int): Array<String> = arrayOf("l" + a)
    fun boxed(a: Int): Int? = if (a > 0) a else null
    fun guarded(s: String): Int =
        try {
            s.toInt()
        } catch (e: NumberFormatException) {
            -1
        } finally {
            hits++
        }
    fun over(a: Int) = a * 2
    fun over(a: Long) = a * 2
    fun overInt() = over(3)
    fun overLong() = over(3L)

    class Builder {
        var size = 0
        fun size(n: Int): Builder {
            size = n
            return this
        }
        fun other(): Other = Other()
        companion object {
            fun create(): Builder = Builder().size(1)
        }
    }
    class Other
    fun built(n: Int) = Builder().size(n).size
    fun created() = Builder.create().size
    fun otherOf() = Builder().other()

    class Outer {
        class Inner { fun twice(a: Int) = a * 2 }
    }
    fun inner(a: Int) = Outer.Inner().twice(a)

    // Bodies that already return the default value.
    fun nothing() {}
    fun zero() = 0
    fun zeroLong() = 0L
    fun no() = false
    fun blank() = ""
    fun none(): Any? = null
    fun noList(): List<Int> = emptyList()
    fun noMap(): Map<String, Int> = emptyMap()
    fun noCodes() = IntArray(0)
    class Self { fun self() = this }

    suspend fun leaf(a: Int): Int = a + 1
    suspend fun twice(a: Int): Int = leaf(a) + leaf(a + 1)
    fun runTwice(a: Int): Any? {
        var result: Any? = null
        val block: suspend () -> Int = { twice(a) }
        block.startCoroutine(Continuation(EmptyCoroutineContext) { r -> result = r.getOrElse { it.javaClass.simpleName } })
        return result
    }

    fun adder(): (Int) -> Int = { it + 1 }
    fun applied(a: Int) = adder()(a)
    """.trimIndent()

class ExtremeOperatorTest {
    private val classes = compileKotlin(SOURCE)
    private val noLookup: Hierarchy = { null }
    private val loader = IsolatedClasses(classes).type("SourceKt").classLoader

    private fun sites(
        method: String,
        cls: String = "SourceKt",
    ) = discover(classes.getValue(cls), ExtremeOperator).filter { it.methodName == method }

    private fun mutant(
        method: String,
        cls: String = "SourceKt",
        descriptor: String? = null,
    ): Map<String, ByteArray> {
        val found = sites(method, cls).single { descriptor == null || it.descriptor == descriptor }
        val bytes = mutate(classes.getValue(cls), ExtremeOperator, found, noLookup)
        assertEquals("", verify(bytes), "$found produced an invalid class")
        return classes + (cls to bytes)
    }

    private fun verify(bytes: ByteArray) =
        StringWriter().also { CheckClassAdapter.verify(ClassReader(bytes), loader, false, PrintWriter(it)) }.toString()

    private fun call(
        loaded: Map<String, ByteArray>,
        method: String,
        vararg args: Any,
    ) = loadAndInvoke(loaded, "SourceKt", method, *args)

    private fun changed(
        method: String,
        vararg args: Any,
    ) = call(classes, method, *args) to call(mutant(method), method, *args)

    @Test
    fun `a void body is removed`() {
        assertEquals(1, call(classes, "touched"))
        assertEquals(0, call(mutant("touch"), "touched"))
        assertEquals("body -> return", sites("touch").single().description)
    }

    @Test
    fun `primitive bodies return zero or false, wide types included`() {
        assertEquals(4 to 0, changed("count", 3))
        assertEquals(6L to 0L, changed("wide", 4L, 2.5))
        assertEquals(2.0 to 0.0, changed("ratio", 3.0, 1.5f))
        assertEquals(1.5f to 0f, changed("half", 3f))
        assertEquals(true to false, changed("big", 11))
        assertEquals('d' to 0.toChar(), changed("letter", 3))
        assertEquals(
            listOf("body -> return 0", "body -> return false"),
            listOf(sites("wide").single().description, sites("big").single().description),
        )
    }

    @Test
    fun `reference bodies return an empty value or null`() {
        assertEquals("n1" to "", changed("name", 1))
        assertEquals(listOf("n1") to emptyList<String>(), changed("names", 1))
        assertEquals(mapOf("k" to 1) to emptyMap<String, Int>(), changed("keys", 1))
        assertEquals(setOf(1) to emptySet<Int>(), changed("unique", 1))
        assertEquals(listOf(1) to emptyList<Int>(), changed("all", 1))
        assertEquals(0, (call(mutant("codes"), "codes", 1) as IntArray).size)
        assertEquals(0, (call(mutant("labels"), "labels", 1) as Array<*>).size)
        assertEquals(1 to null, changed("boxed", 1))
    }

    @Test
    fun `a builder returns this, other own-type returns do not`() {
        assertEquals("body -> return this", sites("size", "Builder").single().description)
        assertEquals(5, call(classes, "built", 5))
        assertEquals(0, call(mutant("size", "Builder"), "built", 5))
        assertEquals("body -> return null", sites("other", "Builder").single().description)
        assertEquals(
            "body -> return null",
            sites("create", "Builder\$Companion").single().description,
        )
    }

    @Test
    fun `a body with try, catch and finally is replaced whole`() {
        assertEquals(-1 to 0, changed("guarded", "x"))
    }

    @Test
    fun `each method is one site at its first line`() {
        assertEquals(listOf(0), sites("touched").map { it.ordinal })
        assertEquals(8, sites("touched").single().line)
        assertEquals(24, sites("guarded").single().line)
    }

    @Test
    fun `overloads and nested classes are separate sites`() {
        assertEquals(listOf("(I)I", "(J)J"), sites("over").map { it.descriptor })
        val intChanged = mutant("over", descriptor = "(I)I")
        assertEquals(0, call(intChanged, "overInt"))
        assertEquals(6L, call(intChanged, "overLong"))
        assertEquals(6 to 0, call(classes, "inner", 3) to call(mutant("twice", "Outer\$Inner"), "inner", 3))
    }

    @Test
    fun `a body that already returns the default is not a site`() {
        val defaults = listOf("nothing", "zero", "zeroLong", "no", "blank", "none", "noList", "noMap", "noCodes")

        assertTrue(defaults.all { sites(it).isEmpty() }, defaults.filter { sites(it).isNotEmpty() }.toString())
        assertTrue(sites("self", "Self").isEmpty())
    }

    @Test
    fun `a suspend function returns null, its continuation's re-entry is not a site`() {
        assertEquals("body -> return null", sites("twice").single().description)
        assertEquals(5, call(classes, "runTwice", 1))
        assertNull(call(mutant("twice"), "runTwice", 1))
        assertEquals(emptyList<Any>(), discover(classes.getValue("SourceKt\$twice\$1"), ExtremeOperator))
    }

    @Test
    fun `a lambda body is a site of its own`() {
        val lambda = discover(classes.getValue("SourceKt"), ExtremeOperator).single { "lambda" in it.methodName }

        val bytes = mutate(classes.getValue("SourceKt"), ExtremeOperator, lambda, noLookup)
        assertEquals("", verify(bytes))
        assertEquals(4 to 0, call(classes, "applied", 3) to call(classes + ("SourceKt" to bytes), "applied", 3))
    }

    @Test
    fun `without debug info the site is the first instruction`() {
        val plain =
            buildClass("NoLines", "square", "(I)I") {
                visitVarInsn(ILOAD, 0)
                visitVarInsn(ILOAD, 0)
                visitInsn(IMUL)
                visitInsn(IRETURN)
            }
        val found = discover(plain, ExtremeOperator).single()
        val bytes = mutate(plain, ExtremeOperator, found, noLookup)

        assertNull(found.line)
        assertEquals("", verify(bytes))
        assertEquals(
            9 to 0,
            loadAndInvoke(mapOf("NoLines" to plain), "NoLines", "square", 3) to
                loadAndInvoke(mapOf("NoLines" to bytes), "NoLines", "square", 3),
        )
    }
}
