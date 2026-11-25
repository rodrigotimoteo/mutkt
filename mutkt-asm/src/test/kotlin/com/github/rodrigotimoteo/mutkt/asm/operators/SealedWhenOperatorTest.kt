package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Found
import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.HierarchyClassWriter
import com.github.rodrigotimoteo.mutkt.asm.NoClasses
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.asm.parse
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// The *Of helpers build the argument from an Int, so tests call the mutated function with plain values.
private val SOURCE =
    """
    sealed class Shape
    class Circle(val r: Int) : Shape()
    class Square(val s: Int) : Shape()
    object Empty : Shape()
    fun shapeOf(kind: Int, n: Int): Shape = if (kind == 0) Circle(n) else if (kind == 1) Square(n) else Empty

    fun area(shape: Shape): Int =
        when (shape) {
            is Circle -> 3 * shape.r
            is Square -> shape.s
            Empty -> 0
        }
    fun areaOf(kind: Int, n: Int) = area(shapeOf(kind, n))

    fun elseBranch(shape: Shape): Int =
        when (shape) {
            is Circle -> 1
            is Square -> 2
            else -> 3
        }
    fun elseBranchOf(kind: Int) = elseBranch(shapeOf(kind, 0))

    fun single(shape: Shape): Int = when (shape) { is Circle -> 1; else -> 2 }
    fun singleOf(kind: Int) = single(shapeOf(kind, 0))

    fun multi(shape: Shape): Int =
        when (shape) {
            is Circle, is Square -> 1
            Empty -> 2
        }
    fun multiOf(kind: Int) = multi(shapeOf(kind, 0))

    fun nested(a: Shape, b: Shape): Int =
        when (a) {
            is Circle -> when (b) { is Circle -> 1; else -> 2 }
            else -> 3
        } + when (b) { is Square -> 10; is Empty -> 20; else -> 30 }
    fun nestedOf(a: Int, b: Int) = nested(shapeOf(a, 0), shapeOf(b, 0))

    fun both(s: Shape, t: Shape): Int = if (s is Circle && t is Square) 1 else 0
    fun lone(s: Shape): Boolean = s is Circle

    sealed interface Op
    class Add(val a: Int) : Op
    class Neg(val a: Int) : Op
    fun run(op: Op): Int = when (op) { is Add -> op.a; is Neg -> -op.a }
    fun runOf(neg: Boolean) = run(if (neg) Neg(5) else Add(5))

    enum class Color { RED, GREEN, BLUE }
    fun colorOf(i: Int) = Color.values()[i]

    fun hex(c: Color): Int = when (c) { Color.RED -> 1; Color.GREEN -> 2; Color.BLUE -> 3 }
    fun hexOf(i: Int) = hex(colorOf(i))

    fun grouped(c: Color): Int = when (c) { Color.RED, Color.GREEN -> 1; Color.BLUE -> 2 }
    fun groupedOf(i: Int) = grouped(colorOf(i))

    fun note(c: Color): String {
        var s = "-"
        when (c) { Color.RED -> s = "r"; Color.GREEN -> s = "g"; else -> {} }
        return s
    }
    fun noteOf(i: Int) = note(colorOf(i))

    fun nullable(c: Color?): Int = when (c) { Color.RED -> 1; null -> 0; else -> 5 }
    fun nullableOf(i: Int) = nullable(if (i < 0) null else colorOf(i))

    fun onlyOne(c: Color): Int = when (c) { Color.RED -> 1; else -> 9 }

    fun dense(c: Color): Int = when (c.ordinal) { 0 -> 10; 1 -> 20; 2 -> 30; else -> 0 }
    fun denseOf(i: Int) = dense(colorOf(i))
    fun sparse(c: Color): Int = when (c.ordinal) { 0 -> 10; 100 -> 20; 1000 -> 30; else -> 0 }

    class Token(val n: Int) { fun ordinal(): Int = n }
    fun token(t: Token): Int = when (t.ordinal()) { 1 -> 10; 2 -> 20; 3 -> 30; else -> 40 }

    fun plain(n: Int): Int = when (n) { 1 -> 10; 2 -> 20; 3 -> 30; else -> 0 }
    fun text(s: String): Int = when (s) { "a" -> 1; "b" -> 2; "c" -> 3; else -> 0 }
    fun none(n: Int): Int = n + 1
    """.trimIndent()

class SealedWhenOperatorTest {
    private val classes = compileKotlin(SOURCE)
    private val hierarchy: Hierarchy = { type ->
        classes[type.replace('/', '.')]
            ?: javaClass.classLoader.getResourceAsStream("$type.class")?.use { it.readBytes() }
    }

    private fun sites(
        method: String,
        lookup: Hierarchy = hierarchy,
    ) = classes.values
        .flatMap {
            discover(
                it,
                SealedWhenOperator,
                hierarchy = lookup,
            )
        }.filter { it.methodName == method }

    private fun descriptions(method: String) = sites(method).map { it.description }

    private fun mutantOf(found: Found): Map<String, ByteArray> =
        classes + (found.className to mutate(classes.getValue(found.className), SealedWhenOperator, found, hierarchy))

    private fun mutant(
        method: String,
        ordinal: Int,
    ) = mutantOf(sites(method).single { it.ordinal == ordinal })

    private fun call(
        method: String,
        vararg args: Any?,
    ) = loadAndInvoke(classes, "SourceKt", method, *args)

    @Test
    fun `a sealed when has one site per branch, object branches included`() {
        assertEquals(
            listOf("instanceof Circle -> false", "instanceof Square -> false", "equals Empty -> false"),
            descriptions("area"),
        )
        assertEquals(listOf(0, 1, 2), sites("area").map { it.ordinal })
    }

    @Test
    fun `each sealed branch mutant stops that branch from matching`() {
        assertEquals(listOf(6, 2, 0), (0..2).map { call("areaOf", it, 2) })
        // Circle skipped: the next tests fail too, so the exhaustive when throws.
        assertEquals(2, loadAndInvoke(mutant("area", 0), "SourceKt", "areaOf", 1, 2))
        assertThrows(
            NoWhenBranchMatchedException::class.java,
        ) { loadAndInvoke(mutant("area", 0), "SourceKt", "areaOf", 0, 2) }
        assertThrows(
            NoWhenBranchMatchedException::class.java,
        ) { loadAndInvoke(mutant("area", 1), "SourceKt", "areaOf", 1, 2) }
        assertThrows(
            NoWhenBranchMatchedException::class.java,
        ) { loadAndInvoke(mutant("area", 2), "SourceKt", "areaOf", 2, 2) }
        assertEquals(6, loadAndInvoke(mutant("area", 2), "SourceKt", "areaOf", 0, 2))
    }

    @Test
    fun `a skipped sealed branch falls to the else path`() {
        assertEquals(listOf("instanceof Circle -> false", "instanceof Square -> false"), descriptions("elseBranch"))
        assertEquals(1, call("elseBranchOf", 0))
        assertEquals(3, loadAndInvoke(mutant("elseBranch", 0), "SourceKt", "elseBranchOf", 0))
        assertEquals(3, loadAndInvoke(mutant("elseBranch", 1), "SourceKt", "elseBranchOf", 1))
        assertEquals(1, loadAndInvoke(mutant("elseBranch", 1), "SourceKt", "elseBranchOf", 0))
    }

    @Test
    fun `conditions of one branch are separate sites`() {
        assertEquals(
            listOf("instanceof Circle -> false", "instanceof Square -> false", "equals Empty -> false"),
            descriptions("multi"),
        )
        // Circle no longer matches its own test and is no Square or Empty either, so the exhaustive when throws.
        assertThrows(
            NoWhenBranchMatchedException::class.java,
        ) { loadAndInvoke(mutant("multi", 0), "SourceKt", "multiOf", 0) }
        assertEquals(1, loadAndInvoke(mutant("multi", 0), "SourceKt", "multiOf", 1))
    }

    @Test
    fun `a sealed interface is covered like a sealed class`() {
        assertEquals(2, sites("run").size)
        assertEquals(-5, call("runOf", true))
        assertThrows(
            NoWhenBranchMatchedException::class.java,
        ) { loadAndInvoke(mutant("run", 1), "SourceKt", "runOf", true) }
        assertEquals(5, loadAndInvoke(mutant("run", 1), "SourceKt", "runOf", false))
    }

    @Test
    fun `each when tests its own subject copy, so a lone test in one when is no site`() {
        // Of four tests only the second when has two; the others test other locals.
        assertEquals(listOf("instanceof Square -> false", "instanceof Empty -> false"), descriptions("nested"))
        assertEquals(listOf(0, 1), sites("nested").map { it.ordinal })
        assertEquals(13, call("nestedOf", 1, 1))
        assertEquals(33, loadAndInvoke(mutant("nested", 0), "SourceKt", "nestedOf", 1, 1))
    }

    @Test
    fun `a single type test of a subject and tests on different locals are not sites`() {
        assertEquals(emptyList<String>(), descriptions("single") + descriptions("both") + descriptions("lone"))
    }

    @Test
    fun `an enum when has one site per body, redirecting its cases to the default`() {
        assertEquals(
            listOf("when switch key 1 -> default", "when switch key 2 -> default", "when switch key 3 -> default"),
            descriptions("hex"),
        )
        assertEquals(listOf(1, 2, 3), (0..2).map { call("hexOf", it) })
        assertThrows(
            NoWhenBranchMatchedException::class.java,
        ) { loadAndInvoke(mutant("hex", 1), "SourceKt", "hexOf", 1) }
        assertEquals(1, loadAndInvoke(mutant("hex", 1), "SourceKt", "hexOf", 0))
        assertEquals(3, loadAndInvoke(mutant("hex", 1), "SourceKt", "hexOf", 2))
    }

    @Test
    fun `cases sharing a body move together`() {
        assertEquals(listOf("when switch key 1,2 -> default", "when switch key 3 -> default"), descriptions("grouped"))
        assertEquals(2, call("groupedOf", 2))
        assertThrows(NoWhenBranchMatchedException::class.java) {
            loadAndInvoke(mutant("grouped", 0), "SourceKt", "groupedOf", 0)
        }
        assertThrows(NoWhenBranchMatchedException::class.java) {
            loadAndInvoke(mutant("grouped", 0), "SourceKt", "groupedOf", 1)
        }
        assertEquals(2, loadAndInvoke(mutant("grouped", 0), "SourceKt", "groupedOf", 2))
    }

    @Test
    fun `an enum when statement without else skips the branch body`() {
        assertEquals(listOf("r", "g", "-"), (0..2).map { call("noteOf", it) })
        assertEquals("-", loadAndInvoke(mutant("note", 0), "SourceKt", "noteOf", 0))
        assertEquals("g", loadAndInvoke(mutant("note", 0), "SourceKt", "noteOf", 1))
    }

    @Test
    fun `the null case of a nullable enum when is a branch, the default-bound case is not`() {
        // Keys: -1 is null, 0 is an unmapped entry (already the default), 1 is RED.
        assertEquals(listOf("when switch key -1 -> default", "when switch key 1 -> default"), descriptions("nullable"))
        assertEquals(0, call("nullableOf", -1))
        assertEquals(5, loadAndInvoke(mutant("nullable", 0), "SourceKt", "nullableOf", -1))
        assertEquals(5, loadAndInvoke(mutant("nullable", 1), "SourceKt", "nullableOf", 0))
    }

    @Test
    fun `an ordinal switch is covered, dense or sparse`() {
        assertEquals(3, sites("dense").size)
        assertEquals(
            listOf("when switch key 0 -> default", "when switch key 100 -> default", "when switch key 1000 -> default"),
            descriptions("sparse"),
        )
        assertEquals(20, call("denseOf", 1))
        assertEquals(0, loadAndInvoke(mutant("dense", 1), "SourceKt", "denseOf", 1))
    }

    @Test
    fun `an ordinal method that is not an enum's is not a switch over a hierarchy`() {
        assertEquals(emptyList<String>(), descriptions("token"))
    }

    @Test
    fun `an ordinal switch needs the enum class, a mapping array needs nothing`() {
        assertEquals(emptyList<Found>(), sites("dense", NoClasses))
        assertEquals(3, sites("hex", NoClasses).size)
    }

    @Test
    fun `whens that are not over a hierarchy have no site`() {
        // A single enum case compiles to a plain comparison, which is a conditional, not a switch.
        assertEquals(
            emptyList<String>(),
            descriptions("onlyOne") + descriptions("plain") + descriptions("text") + descriptions("none"),
        )
    }

    @Test
    fun `every site changes the class and verifies`() {
        val all = classes.values.flatMap { discover(it, SealedWhenOperator, hierarchy = hierarchy) }
        assertTrue(all.size >= 20, "expected the fixtures to yield many sites, got ${all.size}")
        all.forEach { found ->
            val original = classes.getValue(found.className)
            val unchanged = HierarchyClassWriter(hierarchy).also { parse(original).accept(it) }.toByteArray()
            val mutated = mutantOf(found).getValue(found.className)
            assertFalse(mutated.contentEquals(unchanged), "$found left the class unchanged")
            // Invoking links the class, which runs the JVM verifier over every method.
            assertEquals(2, loadAndInvoke(mutantOf(found), "SourceKt", "none", 1), "$found produced an invalid class")
            assertArrayEquals(mutated, mutantOf(found).getValue(found.className))
        }
    }
}
