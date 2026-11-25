package com.github.rodrigotimoteo.mutkt.asm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class OperatorDocTest {
    private val lines =
        File(checkNotNull(System.getProperty("mutkt.operatorsDoc")) { "mutkt.operatorsDoc is not set" }).readLines()

    private val rows =
        lines
            .mapNotNull { Regex("^\\| `([a-z-]+)` \\| (yes|no) \\|").find(it)?.destructured }
            .map { (id, default) -> id to (default == "yes") }

    @Test
    fun `every operator is in the summary table once`() {
        assertEquals(
            Operators.all.map { it.id.value },
            rows.map { it.first },
            "docs/operators.md and Operators.all disagree",
        )
    }

    @Test
    fun `every operator has an examples entry`() {
        val examples = lines.mapNotNull { Regex("^### `([a-z-]+)`$").find(it)?.groupValues?.get(1) }
        assertEquals(Operators.all.map { it.id.value }, examples, "Examples section and Operators.all disagree")
    }

    @Test
    fun `documented default flags match the default set`() {
        assertEquals(Operators.defaults.map { it.id.value }, rows.filter { it.second }.map { it.first })
    }
}
