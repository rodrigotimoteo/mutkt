package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.ToyOperator
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.asm.parse
import com.github.rodrigotimoteo.mutkt.asm.toBytes
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InlinedBodyFilterTest {
    private val classes =
        compileKotlin(
            """
            inline fun twice(a: Int, f: (Int) -> Int): Int = f(a) + f(a + 1)
            inline fun clamp(x: Int): Int = if (x > 10) 10 else x
            fun user(n: Int): Int = twice(n + 1) { it + 10 } + clamp(n)
            fun mapped(l: List<Int>) = l.map { it * 2 }
            inline val Int.dbl: Int get() = this + this
            fun viaGetter(n: Int) = n.dbl + 1
            """.trimIndent(),
        )
    private val file = classes.getValue("SourceKt")
    private val noLookup: (String) -> ByteArray? = { null }

    @Test
    fun `the original inline function keeps its body`() {
        assertEquals(emptyList<String>(), InlinedBodyFilter.noiseIn(classes, "SourceKt", "twice"))
        assertEquals(emptyList<String>(), InlinedBodyFilter.noiseIn(classes, "SourceKt", "clamp"))
        assertEquals(emptyList<String>(), InlinedBodyFilter.noiseIn(classes, "SourceKt", "getDbl"))
    }

    @Test
    fun `an inlined body is noise but the arguments and the lambda bodies are user code`() {
        val noise = InlinedBodyFilter.noiseIn(classes, "SourceKt", "user")

        // a + 1 and f(a) + f(a + 1) of twice, and the comparison of clamp; n + 1, both copies of it + 10 and
        // the final sum stay.
        assertEquals(2, noise.count { it == "IADD" })
        assertEquals(1, noise.count { it == "IF_ICMPLE" })
        assertEquals(4, discover(file, ToyOperator).count { it.methodName == "user" })
        assertEquals(6, ToyOperator.sites(parse(file).methods.single { it.name == "user" }).size)
    }

    @Test
    fun `a standard library inline body is noise, its lambda is not`() {
        val noise = InlinedBodyFilter.noiseIn(classes, "SourceKt", "mapped")

        assertEquals(true, "INVOKEINTERFACE Iterator.hasNext" in noise)
        assertEquals(true, "IFEQ" in noise)
        assertEquals(false, "IMUL" in noise)
    }

    @Test
    fun `an inlined property getter body is noise in the caller only`() {
        // this + this is the copy; the + 1 of the caller is not.
        assertEquals(1, InlinedBodyFilter.noiseIn(classes, "SourceKt", "viaGetter").count { it == "IADD" })
    }

    @Test
    fun `without debug info nothing is recognised`() {
        val node = parse(file).also { n -> n.methods.forEach { it.localVariables = null } }

        val user = node.methods.single { it.name == "user" }

        assertEquals(
            ToyOperator.sites(user).size,
            discover(node.toBytes(), ToyOperator).count { it.methodName == "user" },
        )
    }

    @Test
    fun `ordinals index the filtered sites in discover and mutate`() {
        val sites = discover(file, ToyOperator).filter { it.methodName == "user" }
        val mutated = { ordinal: Int ->
            val bytes = mutate(file, ToyOperator, sites.single { it.ordinal == ordinal }, noLookup)
            loadAndInvoke(classes + ("SourceKt" to bytes), "SourceKt", "user", 5)
        }

        assertEquals(listOf(0, 1, 2, 3), sites.map { it.ordinal })
        assertEquals(38, loadAndInvoke(classes, "SourceKt", "user", 5))
        // n + 1 becomes n - 1: twice(4) = 14 + 15, plus clamp(5).
        assertEquals(34, mutated(0))
        // The last site is the sum of twice and clamp, not the copy of twice's sum.
        assertEquals(28, mutated(3))
    }
}
