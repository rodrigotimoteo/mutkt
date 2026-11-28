package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.IsolatedClasses
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Modifier

private val SOURCE =
    """
    fun anyOf(xs: List<Int>) = xs.any()
    fun noneOf(xs: List<Int>) = xs.none()
    fun head(xs: List<Int>) = xs.first()
    fun headOrNull(xs: List<Int>) = xs.firstOrNull()
    fun tailOrNull(xs: Iterable<Int>) = xs.lastOrNull()
    fun lowest(xs: List<Int>) = xs.min()
    fun lowestDouble(xs: List<Double>) = xs.min() + 0.5
    fun highestOrNull(xs: List<Long>) = xs.maxOrNull()
    fun highestLong(xs: LongArray) = xs.max() + 1L
    fun shortest(xs: List<String>) = xs.minWith(compareBy { it.length })
    fun prefix(xs: List<Int>) = xs.take(2)
    fun prefixText(s: String) = s.take(2)
    fun ascending(xs: List<Int>) = xs.sorted()
    fun ascendingArray(xs: IntArray) = xs.sorted()
    fun initial(s: String) = s.first()
    fun evens(xs: List<Int>) = xs.asSequence().filter { it % 2 == 0 }.toList()
    fun rest(xs: List<Int>) = xs.asSequence().drop(1).toList()
    fun span(xs: List<Int>) = xs.last() - xs.first()
    fun pick(xs: List<Int>) = xs.first()
    fun pick(s: String) = s.first()
    fun pickList(xs: List<Int>) = pick(xs)
    fun pickText(s: String) = pick(s)
    fun nested(xs: List<Int>) = Outer.Inner().top(xs)

    class Outer {
        class Inner {
            fun top(xs: List<Int>) = xs.sortedDescending().first()
        }
    }

    fun inline(xs: List<Int>, s: String, m: Map<String, Int>): Boolean =
        xs.filter { it > 1 }.all { it > 0 } && xs.any { it > 2 } && xs.isNotEmpty() && !s.isEmpty() &&
            xs.minBy { -it } > 0 && m.any() && xs.first { it > 0 } > 0
    """.trimIndent()

private val STDLIB_OWNERS =
    listOf(
        "kotlin.collections.CollectionsKt",
        "kotlin.collections.ArraysKt",
        "kotlin.sequences.SequencesKt",
        "kotlin.text.StringsKt",
    )

class StdlibSwapOperatorTest {
    private val classes = compileKotlin(SOURCE)
    private val noLookup: Hierarchy = { null }
    private val loader = IsolatedClasses(classes).type("SourceKt").classLoader

    private fun sites(
        method: String,
        cls: String = "SourceKt",
    ) = discover(classes.getValue(cls), StdlibSwapOperator).filter { it.methodName == method }

    private fun mutant(
        method: String,
        cls: String = "SourceKt",
        descriptor: String? = null,
        ordinal: Int = 0,
    ): Map<String, ByteArray> {
        val found =
            sites(method, cls).single { it.ordinal == ordinal && (descriptor == null || it.descriptor == descriptor) }
        val bytes = mutate(classes.getValue(cls), StdlibSwapOperator, found, noLookup)
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

    private fun swaps(
        method: String,
        vararg args: Any,
    ) = call(classes, method, *args) to call(mutant(method), method, *args)

    @Test
    fun `each pair swaps both ways`() {
        val xs = listOf(3, 1, 4, 2)
        assertEquals(true to false, swaps("anyOf", xs))
        assertEquals(false to true, swaps("noneOf", xs))
        assertEquals(3 to 2, swaps("head", xs))
        assertEquals(3 to 2, swaps("headOrNull", xs))
        assertEquals(2 to 3, swaps("tailOrNull", xs))
        assertEquals(1 to 4, swaps("lowest", xs))
        assertEquals(listOf(3, 1) to listOf(4, 2), swaps("prefix", xs))
        assertEquals(listOf(1, 2, 3, 4) to listOf(4, 3, 2, 1), swaps("ascending", xs))
        assertEquals(listOf(4, 2) to listOf(3, 1), swaps("evens", xs))
        assertEquals(listOf(1, 4, 2) to listOf(3), swaps("rest", xs))
    }

    @Test
    fun `wide results, arrays, strings and comparators keep a valid stack`() {
        assertEquals(1.5 to 3.5, swaps("lowestDouble", listOf(1.0, 3.0)))
        assertEquals(10L to 2L, swaps("highestLong", longArrayOf(1, 9)))
        assertEquals(9L to 1L, swaps("highestOrNull", listOf(1L, 9L)))
        assertEquals("a" to "ccc", swaps("shortest", listOf("ccc", "a")))
        assertEquals("ab" to "c", swaps("prefixText", "abc"))
        assertEquals(listOf(1, 3) to listOf(3, 1), swaps("ascendingArray", intArrayOf(3, 1)))
        assertEquals('a' to 'c', swaps("initial", "abc"))
    }

    @Test
    fun `sites name the owner and both functions`() {
        val names = listOf("anyOf", "lowest", "prefixText", "evens", "ascendingArray")
        assertEquals(
            listOf(
                "CollectionsKt.any -> none",
                "CollectionsKt.minOrThrow -> maxOrThrow",
                "StringsKt.take -> drop",
                "SequencesKt.filter -> filterNot",
                "ArraysKt.sorted -> sortedDescending",
            ),
            names.map { sites(it).single().description },
        )
    }

    @Test
    fun `each call of a method is its own ordinal`() {
        val xs = listOf(3, 1, 4, 2)
        assertEquals(
            listOf("CollectionsKt.last -> first", "CollectionsKt.first -> last"),
            sites("span").map { it.description },
        )
        assertEquals(-1, call(classes, "span", xs))
        assertEquals(0, call(mutant("span", ordinal = 0), "span", xs))
        assertEquals(0, call(mutant("span", ordinal = 1), "span", xs))
    }

    @Test
    fun `overloads are told apart by descriptor`() {
        val list = "(Ljava/util/List;)I"
        val text = "(Ljava/lang/String;)C"
        assertEquals(listOf(list, text), sites("pick").map { it.descriptor })
        val listChanged = mutant("pick", descriptor = list)
        val textChanged = mutant("pick", descriptor = text)

        assertEquals(listOf(3, 'a'), listOf(call(classes, "pickList", listOf(3, 2)), call(classes, "pickText", "ab")))
        assertEquals(
            listOf(2, 'a'),
            listOf(call(listChanged, "pickList", listOf(3, 2)), call(listChanged, "pickText", "ab")),
        )
        assertEquals(
            listOf(3, 'b'),
            listOf(call(textChanged, "pickList", listOf(3, 2)), call(textChanged, "pickText", "ab")),
        )
    }

    @Test
    fun `instance methods of nested classes are sites`() {
        assertEquals(2, sites("top", "Outer\$Inner").size)
        assertEquals(3, call(classes, "nested", listOf(1, 3)))
        assertEquals(1, call(mutant("top", "Outer\$Inner", ordinal = 0), "nested", listOf(1, 3)))
        assertEquals(1, call(mutant("top", "Outer\$Inner", ordinal = 1), "nested", listOf(1, 3)))
    }

    @Test
    fun `inline lambda overloads, isEmpty, Map calls and asSequence are not sites`() {
        assertEquals(emptyList<Any>(), sites("inline"))
        assertEquals(1, sites("evens").size)
    }

    // The rename is valid only if the partner has the same descriptor in the same owner, for every overload.
    @Test
    fun `every swapped stdlib function has a partner with the same descriptor`() {
        val checked =
            STDLIB_OWNERS.sumOf { name ->
                val statics = Class.forName(name).methods.filter { Modifier.isStatic(it.modifiers) }
                val signatures = statics.map { it.name to Type.getMethodDescriptor(it) }.toSet()
                val owner = name.replace('.', '/')
                signatures.count { (method, desc) ->
                    val partner = stdlibPartner(owner, method, desc) ?: return@count false
                    assertTrue((partner to desc) in signatures, "$name.$method$desc has no $partner$desc")
                    true
                }
            }
        assertTrue(checked > 100, "only $checked stdlib functions checked")
    }
}
