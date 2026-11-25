package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.NoClasses
import com.github.rodrigotimoteo.mutkt.asm.discover
import com.github.rodrigotimoteo.mutkt.asm.mutate
import com.github.rodrigotimoteo.mutkt.asm.operators.NonVoidMethodCallsOperator
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import com.github.rodrigotimoteo.mutkt.testing.loadAndInvoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StringWhenFilterTest {
    private val classes =
        compileKotlin(
            """
            fun lookup(s: String): Int = when (s) { "a" -> 1; "b" -> 2; "c" -> 4; else -> 3 }
            fun nullable(s: String?): Int = when (s) { "a" -> 1; "b" -> 2; "c" -> 4; null -> 5; else -> 3 }
            fun grouped(s: String): Int = when (s) { "a", "b" -> 1; "c" -> 2; else -> 3 }
            fun short(s: String): Int = when (s) { "a" -> 1; "b" -> 2; else -> 3 }
            fun hashed(s: String): Int = when (s.hashCode()) { 1 -> 5; 2 -> 6; 100 -> 7; else -> 8 }
            fun plain(s: String, t: String): Boolean = s.hashCode() > 0 && s.equals(t)
            fun touch(n: Int): Int = n
            fun source(): String = "a"
            fun collide(s: String): Int = when (s) { "Aa" -> touch(1); "BB" -> touch(2); "C" -> touch(3); else -> touch(4) }
            fun statement(s: String): Int {
                var r = 0
                when (s) { "a" -> r = touch(1); "b" -> r = touch(2); "c" -> r = touch(3) }
                return r
            }
            fun bound(): Int =
                when (val x = source()) { "a" -> touch(1); "b" -> touch(2); "c" -> touch(3); else -> touch(4) }
            fun viaLambda(): (String) -> Int =
                { s -> when (s) { "a" -> touch(1); "b" -> touch(2); "c" -> touch(3); else -> touch(4) } }
            fun comparing(s: String, other: String): Boolean =
                when (s.hashCode()) { 1 -> other.equals("red"); 2 -> other.equals("green"); 3 -> other.equals("blue"); else -> false }
            fun sameSubject(s: String): Boolean =
                when (s.hashCode()) { 1 -> s.equals("red"); 2 -> s.equals("green"); 3 -> s.equals("blue"); else -> false }
            fun branching(s: String): Int =
                when (s.hashCode()) { 1 -> if (s.equals("red")) 5 else 6; 2 -> if (s.equals("green")) 7 else 8; else -> 9 }
            """.trimIndent(),
        )

    private val lookupNoise = listOf("INVOKEVIRTUAL String.hashCode") + List(3) { "INVOKEVIRTUAL String.equals" }

    @Test
    fun `the hash lookup and the equals chain are noise`() {
        assertEquals(lookupNoise, StringWhenFilter.noiseIn(classes, "SourceKt", "lookup"))
        assertEquals(
            listOf("INVOKEVIRTUAL String.hashCode") +
                List(3) {
                    "INVOKEVIRTUAL String.equals"
                },
            StringWhenFilter.noiseIn(classes, "SourceKt", "nullable"),
        )
        assertEquals(lookupNoise, StringWhenFilter.noiseIn(classes, "SourceKt", "grouped"))
    }

    @Test
    fun `a when without a hash lookup has no noise`() {
        assertEquals(emptyList<String>(), StringWhenFilter.noiseIn(classes, "SourceKt", "short"))
    }

    @Test
    fun `hashCode and equals written by the user are not noise`() {
        assertEquals(emptyList<String>(), StringWhenFilter.noiseIn(classes, "SourceKt", "hashed"))
        assertEquals(emptyList<String>(), StringWhenFilter.noiseIn(classes, "SourceKt", "plain"))
    }

    @Test
    fun `equals results that are returned or branched on, not a dispatch guard, are not noise`() {
        listOf("comparing", "sameSubject", "branching").forEach {
            assertEquals(emptyList<String>(), StringWhenFilter.noiseIn(classes, "SourceKt", it), it)
        }
    }

    @Test
    fun `a user hash switch keeps its hash call and comparisons as method call sites`() {
        val bytes = classes.getValue("SourceKt")
        val sites = discover(bytes, NonVoidMethodCallsOperator).filter { it.methodName == "comparing" }

        assertEquals(
            listOf("String.hashCode", "String.equals", "String.equals", "String.equals"),
            sites.map { it.description.substringAfterLast(' ') },
        )
        // A string whose hash is 1 reaches the first case; removing a call yields false (hash 0, or equals false).
        val subject = Char(1).toString()
        val results =
            sites.map {
                val mutant = mutate(bytes, NonVoidMethodCallsOperator, it, NoClasses)
                loadAndInvoke(classes + ("SourceKt" to mutant), "SourceKt", "comparing", subject, "red")
            }
        assertEquals(listOf(false, false, true, true), results)
    }

    @Test
    fun `real compiler shapes keep their dispatch filtered and their branch bodies mutable`() {
        // The lambda body is a static method named after its function (viaLambda$lambda$0).
        val expected =
            mapOf(
                "collide" to List(4) { "touch" },
                "statement" to List(3) { "touch" },
                "bound" to listOf("source") + List(4) { "touch" },
                "viaLambda" to List(4) { "touch" },
            )
        val sites = discover(classes.getValue("SourceKt"), NonVoidMethodCallsOperator)
        expected.forEach { (method, calls) ->
            val found = sites.filter { it.methodName.startsWith(method) }.map { it.description.substringAfterLast('.') }
            assertEquals(calls, found, method)
        }
    }

    @Test
    fun `the plumbing of a generated string when is no method call site`() {
        val generated = setOf("lookup", "nullable", "grouped")
        val sites = discover(classes.getValue("SourceKt"), NonVoidMethodCallsOperator)

        assertEquals(emptyList<String>(), sites.filter { it.methodName in generated }.map { it.description })
    }
}
