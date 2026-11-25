package com.github.rodrigotimoteo.mutkt.asm.filters

import com.github.rodrigotimoteo.mutkt.asm.isMutable
import com.github.rodrigotimoteo.mutkt.asm.parse
import com.github.rodrigotimoteo.mutkt.testing.compileKotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhenMappingsFilterTest {
    private val classes =
        compileKotlin(
            """
            enum class Color { R, G, B }
            fun hex(c: Color): Int = when (c) { Color.R -> 1 + 0; Color.G -> 2 + 0; Color.B -> 3 + 0 }
            fun nullable(c: Color?): String = when (c) { Color.R -> "r"; null -> "n"; else -> "x" }
            class Holder(val c: Color) { fun h() = when (c) { Color.R -> 1; else -> 2 } }
            val table = intArrayOf(5, 6, 7)
            fun idx(c: Color) = table[c.ordinal] + c.ordinal
            """.trimIndent(),
        )

    private val ordinal = listOf("INVOKEVIRTUAL Color.ordinal")

    @Test
    fun `the ordinal feeding the mapping table is noise`() {
        assertEquals(ordinal, WhenMappingsFilter.noiseIn(classes, "SourceKt", "hex"))
        assertEquals(ordinal, WhenMappingsFilter.noiseIn(classes, "SourceKt", "nullable"))
        assertEquals(ordinal, WhenMappingsFilter.noiseIn(classes, "Holder", "h"))
    }

    @Test
    fun `an ordinal indexing the user's own array is not noise`() {
        assertEquals(emptyList<String>(), WhenMappingsFilter.noiseIn(classes, "SourceKt", "idx"))
    }

    // The mapping class holds only its initializer, which the skip rules already ignore.
    @Test
    fun `the mapping class has no mutable method`() {
        val mapping = parse(classes.getValue("SourceKt\$WhenMappings"))

        assertTrue(mapping.methods.none(::isMutable))
    }
}
