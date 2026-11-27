package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.Found
import com.github.rodrigotimoteo.mutkt.asm.Hierarchy
import com.github.rodrigotimoteo.mutkt.asm.HierarchyClassWriter
import com.github.rodrigotimoteo.mutkt.asm.NoClasses
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.asm.parse
import com.github.rodrigotimoteo.mutkt.testing.IsolatedClasses
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import java.io.PrintWriter
import java.io.StringWriter

private val SOURCE =
    """
    fun month(n: Int): Int = when (n) { 1 -> 31; 2 -> 28; 3 -> 30; else -> 0 }
    fun sparse(n: Int): Int = when (n) { 1 -> 10; 100 -> 20; 1000 -> 30; else -> 0 }
    fun grouped(n: Int): Int = when (n) { 1, 2 -> 10; 3 -> 20; else -> 0 }
    fun statement(n: Int): Int {
        var r = 0
        when (n) { 1 -> r = 5; 2 -> r = 6; 3 -> r = 7 }
        return r
    }
    fun wide(a: Long, n: Int): Long = a + when (n) { 1 -> 1L; 2 -> 2L; 3 -> 3L; else -> 0L }
    fun letter(c: Char): Int = when (c) { 'a' -> 1; 'b' -> 2; 'c' -> 3; else -> 0 }

    fun code(s: String): Int = when (s) { "one" -> 1; "two" -> 2; "three" -> 3; else -> -1 }
    fun maybe(s: String?): Int = when (s) { "a" -> 1; "b" -> 2; "c" -> 3; null -> 9; else -> 0 }
    fun shared(s: String): Int = when (s) { "a", "b" -> 1; "c" -> 3; else -> 0 }
    fun pair(s: String): Int = when (s) { "a" -> 1; "b" -> 2; else -> 0 }
    fun collide(s: String): Int = when (s) { "Aa" -> 1; "BB" -> 2; "c" -> 3; "d" -> 4; else -> 0 }
    fun hashed(s: String): Int = when (s.hashCode()) { 97 -> 1; 98 -> 2; 99 -> 3; else -> 0 }

    enum class Color { RED, GREEN, BLUE }
    fun hex(c: Color): Int = when (c) { Color.RED -> 1; Color.GREEN -> 2; Color.BLUE -> 3 }
    fun dense(c: Color): Int = when (c.ordinal) { 0 -> 10; 1 -> 20; 2 -> 30; else -> 0 }
    class Token(val n: Int) { fun ordinal(): Int = n }
    fun token(t: Token): Int = when (t.ordinal()) { 1 -> 10; 2 -> 20; 3 -> 30; else -> 40 }

    suspend fun leaf(a: Int): Int = a + 1
    suspend fun twice(a: Int): Int = leaf(a) + leaf(a + 1)

    fun days(n: Int): Int = when (n) { 1 -> 31; 2 -> 28; 3 -> 31; else -> 30 }
    fun days(n: Int, leap: Boolean): Int = when (n) { 2 -> if (leap) 29 else 28; 4 -> 30; 6 -> 30; else -> 31 }
    class Calendar { class Week { fun day(n: Int): String = when (n) { 0 -> "sun"; 6 -> "sat"; 3 -> "wed"; else -> "-" } } }
    var seen = 0
    fun guarded(n: Int, fail: Boolean): Int {
        var r = 0
        try {
            if (fail) throw IllegalStateException("x")
            r = n
        } finally {
            seen = when (n) { 1 -> 10; 2 -> 20; 3 -> 30; else -> 0 }
        }
        return r + seen
    }
    fun seenAfterFailure(n: Int): Int {
        try { guarded(n, true) } catch (e: IllegalStateException) { }
        return seen
    }
    fun none(n: Int): Int = n + 1
    """.trimIndent()

class WhenSwitchOperatorTest {
    private val classes = compileKotlin(SOURCE)
    private val hierarchy: Hierarchy = { type ->
        classes[type.replace('/', '.')]
            ?: javaClass.classLoader.getResourceAsStream("$type.class")?.use { it.readBytes() }
    }

    private fun all(lookup: Hierarchy = hierarchy) =
        classes.values.flatMap { discover(it, WhenSwitchOperator, hierarchy = lookup) }

    private fun sites(
        method: String,
        lookup: Hierarchy = hierarchy,
    ) = all(lookup).filter { it.methodName == method }

    private fun descriptions(method: String) = sites(method).map { it.description }

    private fun mutantOf(found: Found): Map<String, ByteArray> =
        classes + (found.className to mutate(classes.getValue(found.className), WhenSwitchOperator, found, hierarchy))

    private fun call(
        method: String,
        ordinal: Int?,
        vararg args: Any?,
    ): Any? {
        val loaded = ordinal?.let { mutantOf(sites(method).single { it.ordinal == ordinal }) } ?: classes
        return loadAndInvoke(loaded, "SourceKt", method, *args)
    }

    private fun verify(
        mutant: Map<String, ByteArray>,
        name: String,
    ): String {
        val loader = IsolatedClasses(mutant).type("SourceKt").classLoader
        val log = StringWriter()
        CheckClassAdapter.verify(ClassReader(mutant.getValue(name)), loader, false, PrintWriter(log))
        return log.toString()
    }

    @Test
    fun `an Int when has a site per case and one for the default`() {
        assertEquals(
            listOf(
                "when case 1 -> default",
                "when case 2 -> default",
                "when case 3 -> default",
                "when default -> case 1",
            ),
            descriptions("month"),
        )
        assertEquals(listOf(0, 1, 2, 3), sites("month").map { it.ordinal })
    }

    @Test
    fun `a case sent to the default takes the else branch, the others keep theirs`() {
        assertEquals(28, call("month", null, 2))
        assertEquals(0, call("month", 1, 2))
        assertEquals(31, call("month", 1, 1))
        assertEquals(0, call("sparse", 2, 1000))
        assertEquals(10, call("sparse", 2, 1))
    }

    @Test
    fun `the default sent to the first case runs its body for unmatched values`() {
        assertEquals(0, call("month", null, 7))
        assertEquals(31, call("month", 3, 7))
        assertEquals(28, call("month", 3, 2))
        assertEquals(10, call("sparse", 3, 5))
    }

    @Test
    fun `cases sharing a body move together`() {
        assertEquals(
            listOf("when case 1,2 -> default", "when case 3 -> default", "when default -> case 1"),
            descriptions("grouped"),
        )
        assertEquals(listOf(0, 0, 20), (1..3).map { call("grouped", 0, it) })
    }

    @Test
    fun `a when statement without else, a long under the switch and a Char subject are covered`() {
        assertEquals(5, call("statement", 3, 9))
        assertEquals(0, call("statement", 1, 2))
        assertEquals(12L, call("wide", null, 10L, 2))
        assertEquals(10L, call("wide", 1, 10L, 2))
        assertEquals(11L, call("wide", 3, 10L, 9))
        assertEquals(0, call("letter", 1, 'b'))
        assertEquals(1, call("letter", 3, 'z'))
    }

    @Test
    fun `a String when names its constants and its default runs the first body, not its equals check`() {
        assertEquals(
            listOf(
                "when case \"one\" -> default",
                "when case \"two\" -> default",
                "when case \"three\" -> default",
                "when default -> case \"one\"",
            ),
            descriptions("code"),
        )
        assertEquals(-1, call("code", 1, "two"))
        assertEquals(3, call("code", 1, "three"))
        // Sent to the equals check of "one", "four" would fail it and return to the default: an equivalent mutant.
        assertEquals(1, call("code", 3, "four"))
        assertEquals(2, call("code", 3, "two"))
    }

    @Test
    fun `a nullable String when keeps its null branch`() {
        assertEquals(4, sites("maybe").size)
        assertEquals(9, call("maybe", 3, null))
        assertEquals(1, call("maybe", 3, "z"))
        assertEquals(0, call("maybe", 1, "b"))
    }

    @Test
    fun `String constants sharing a body are separate sites`() {
        assertEquals(
            listOf("when case \"a\" -> default", "when case \"b\" -> default", "when case \"c\" -> default"),
            descriptions("shared").take(3),
        )
        assertEquals(0, call("shared", 1, "b"))
        assertEquals(1, call("shared", 1, "a"))
    }

    @Test
    fun `String constants sharing a hash are separate sites`() {
        // "Aa" and "BB" have the same hash: one case checks both.
        assertEquals(
            listOf(
                "when case \"c\" -> default",
                "when case \"d\" -> default",
                "when case \"Aa\" -> default",
                "when case \"BB\" -> default",
                "when default -> case \"c\"",
            ),
            descriptions("collide"),
        )
        assertEquals(listOf(0, 2), listOf("Aa", "BB").map { call("collide", 2, it) })
        assertEquals(listOf(1, 0), listOf("Aa", "BB").map { call("collide", 3, it) })
        assertEquals(3, call("collide", 4, "zz"))
    }

    @Test
    fun `a when in a finally block is one site per branch that changes every copy`() {
        // kotlinc copies the finally body to the normal and the exceptional path.
        assertEquals(4, sites("guarded").size)
        val mutant = mutantOf(sites("guarded").single { it.description == "when case 2 -> default" })
        assertEquals(22, call("guarded", null, 2, false))
        assertEquals(20, call("seenAfterFailure", null, 2))
        assertEquals(2, loadAndInvoke(mutant, "SourceKt", "guarded", 2, false))
        assertEquals(0, loadAndInvoke(mutant, "SourceKt", "seenAfterFailure", 2))
        assertEquals(10, loadAndInvoke(mutant, "SourceKt", "seenAfterFailure", 1))
    }

    @Test
    fun `a when (s hashCode) is an Int when`() {
        assertEquals("when default -> case 97", descriptions("hashed").last())
        assertEquals(1, call("hashed", 3, "zz"))
    }

    @Test
    fun `a two-branch String when, enum whens and coroutine state machines have no site`() {
        // A String when needs three branches for a switch. A switch on any ordinal() call is skipped, enum or not
        // and whatever classes the generator can read.
        val none = listOf("pair", "hex", "dense", "token", "leaf", "twice", "invokeSuspend", "none")
        assertEquals(emptyList<String>(), none.flatMap { descriptions(it) })
        assertEquals(emptyList<Found>(), sites("dense", NoClasses) + sites("token", NoClasses))
    }

    @Test
    fun `overloads and nested classes have their own sites`() {
        val days = sites("days")
        assertEquals(setOf("(I)I", "(IZ)I"), days.map { it.descriptor }.toSet())
        assertEquals(listOf(4, 4), days.groupBy { it.descriptor }.values.map { it.size })
        val leapFeb = days.first { it.descriptor == "(IZ)I" }
        assertEquals("when case 2 -> default", leapFeb.description)
        assertEquals(31, loadAndInvoke(mutantOf(leapFeb), "SourceKt", "days", 2, true))
        assertEquals(28, loadAndInvoke(mutantOf(leapFeb), "SourceKt", "days", 2))
        assertEquals(listOf("Calendar\$Week"), sites("day").map { it.className }.distinct())
        assertEquals(
            listOf(
                "when case 0 -> default",
                "when case 3 -> default",
                "when case 6 -> default",
                "when default -> case 0",
            ),
            descriptions("day"),
        )
    }

    @Test
    fun `every site changes the class and verifies`() {
        val found = all()
        assertEquals(60, found.size)
        found.forEach { site ->
            val original = classes.getValue(site.className)
            val unchanged = HierarchyClassWriter(hierarchy).also { parse(original).accept(it) }.toByteArray()
            val mutated = mutantOf(site).getValue(site.className)
            assertFalse(mutated.contentEquals(unchanged), "$site left the class unchanged")
            assertEquals("", verify(mutantOf(site), site.className), "$site failed the ASM verifier")
            // Invoking links the class, which runs the JVM verifier over every method.
            assertEquals(2, loadAndInvoke(mutantOf(site), "SourceKt", "none", 1), "$site produced an invalid class")
        }
    }
}
