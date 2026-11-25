package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
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
}
