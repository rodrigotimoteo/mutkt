package com.github.rodrigotimoteo.mutkt.asm.operators

import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConditionalsBoundaryOperatorTest {
    private val op = ConditionalsBoundaryOperator
    private val classes =
        compileKotlin(
            """
            fun intLt(a: Int, b: Int) = a < b
            fun intLe(a: Int, b: Int) = a <= b
            fun intGt(a: Int, b: Int) = a > b
            fun intGe(a: Int, b: Int) = a >= b
            fun zeroLt(a: Int) = a < 0
            fun zeroGe(a: Int) = a >= 0
            fun longLt(a: Long, b: Long) = a < b
            fun longGe(a: Long, b: Long) = a >= b
            fun doubleLe(a: Double, b: Double) = a <= b
            fun doubleGt(a: Double, b: Double) = a > b
            fun twoSites(a: Int, b: Int) = if (a < b && b <= 10) 1 else 0
            fun equality(a: Int, b: Int) = a == b || a != b
            fun identity(a: String, b: String) = a === b || a !== b
            fun nulls(a: String?) = a == null || a != null
            fun flag(a: Boolean) = if (a) 1 else 0

            fun forLoop(n: Int): Int {
                var count = 0
                for (i in 0 until n) count++
                return count
            }
            fun whileLoop(n: Int): Int {
                var i = 0
                var count = 0
                while (i < n) { i++; count++ }
                return count
            }
            fun doWhile(n: Int): Int {
                var i = 0
                var count = 0
                do { i++; count++ } while (i < n)
                return count
            }

            class Box(val n: Int) {
                fun below(limit: Int) = n < limit
                fun below(limit: Long) = n.toLong() <= limit
            }
            """.trimIndent(),
        )
    private val source = classes.getValue("SourceKt")

    private fun descriptions(method: String) =
        discover(source, op)
            .filter {
                it.methodName == method
            }.map { it.description }

    private fun mutantResult(
        method: String,
        vararg args: Any?,
    ): Any? {
        val site = discover(source, op).single { it.methodName == method }
        val mutant = mutate(source, op, site) { null }
        return loadAndInvoke(classes + ("SourceKt" to mutant), "SourceKt", method, *args)
    }

    @Test
    fun `int comparisons between two values move the boundary`() {
        assertEquals(listOf("if_icmpge -> if_icmpgt"), descriptions("intLt"))
        assertEquals(listOf("if_icmpgt -> if_icmpge"), descriptions("intLe"))
        assertEquals(listOf("if_icmple -> if_icmplt"), descriptions("intGt"))
        assertEquals(listOf("if_icmplt -> if_icmple"), descriptions("intGe"))
    }

    @Test
    fun `each mutant flips the result exactly at equality`() {
        assertEquals(true, mutantResult("intLt", 1, 1))
        assertEquals(false, mutantResult("intLe", 1, 1))
        assertEquals(true, mutantResult("intGt", 1, 1))
        assertEquals(false, mutantResult("intGe", 1, 1))
    }

    @Test
    fun `mutants keep the original result away from the boundary`() {
        assertEquals(true, mutantResult("intLt", 0, 1))
        assertEquals(false, mutantResult("intLt", 2, 1))
        assertEquals(true, mutantResult("intGe", 2, 1))
        assertEquals(false, mutantResult("intGe", 0, 1))
    }

    @Test
    fun `comparisons with zero use the single-operand jumps`() {
        assertEquals(listOf("ifge -> ifgt"), descriptions("zeroLt"))
        assertEquals(listOf("iflt -> ifle"), descriptions("zeroGe"))
        assertEquals(true, mutantResult("zeroLt", 0))
        assertEquals(false, mutantResult("zeroGe", 0))
    }

    // LCMP, DCMPL and DCMPG push an int that IFxx then tests, so Long and Double need no opcodes of their own.
    @Test
    fun `long and double comparisons are mutated through the IF jump after the compare`() {
        assertEquals(listOf("ifge -> ifgt"), descriptions("longLt"))
        assertEquals(listOf("iflt -> ifle"), descriptions("longGe"))
        assertEquals(listOf("ifgt -> ifge"), descriptions("doubleLe"))
        assertEquals(listOf("ifle -> iflt"), descriptions("doubleGt"))
        assertEquals(true, mutantResult("longLt", 5L, 5L))
        assertEquals(false, mutantResult("longGe", 5L, 5L))
        assertEquals(false, mutantResult("doubleLe", 1.5, 1.5))
        assertEquals(true, mutantResult("doubleGt", 1.5, 1.5))
    }

    @Test
    fun `ordinals count the operator's sites within one method`() {
        val found = discover(source, op).filter { it.methodName == "twoSites" }

        fun run(
            ordinal: Int,
            vararg args: Any?,
        ): Any? {
            val mutant = mutate(source, op, found[ordinal]) { null }
            return loadAndInvoke(classes + ("SourceKt" to mutant), "SourceKt", "twoSites", *args)
        }

        assertEquals(listOf(0, 1), found.map { it.ordinal })
        assertEquals(1, loadAndInvoke(classes, "SourceKt", "twoSites", 1, 10))
        assertEquals(1, run(0, 1, 1))
        assertEquals(0, run(1, 1, 10))
    }

    // Each mutant runs one extra iteration and still terminates for a finite bound.
    @Test
    fun `loop bounds gain one iteration`() {
        assertEquals(listOf("if_icmpge -> if_icmpgt"), descriptions("forLoop"))
        assertEquals(listOf("if_icmpge -> if_icmpgt"), descriptions("whileLoop"))
        assertEquals(listOf("if_icmplt -> if_icmple"), descriptions("doWhile"))
        listOf("forLoop", "whileLoop", "doWhile").forEach { method ->
            assertEquals(1, mutantResult(method, 0), method)
            assertEquals(4, mutantResult(method, 3), method)
        }
    }

    @Test
    fun `equality, identity, null and boolean tests are not sites`() {
        listOf(
            "equality",
            "identity",
            "nulls",
            "flag",
        ).forEach { assertEquals(emptyList<String>(), descriptions(it), it) }
    }

    @Test
    fun `instance methods and overloads are separate sites told apart by descriptor`() {
        val found = discover(classes.getValue("Box"), op)

        assertEquals(
            listOf("(I)Z" to "if_icmpge -> if_icmpgt", "(J)Z" to "ifgt -> ifge"),
            found.map { it.descriptor to it.description },
        )
    }
}
