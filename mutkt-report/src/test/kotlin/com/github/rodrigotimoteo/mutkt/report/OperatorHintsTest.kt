package com.github.rodrigotimoteo.mutkt.report

import com.github.rodrigotimoteo.mutkt.domain.OperatorId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

// mutkt-asm's OperatorDocTest holds docs/operators.md equal to its registry, so matching the doc matches the registry
// without this adapter depending on that one.
class OperatorHintsTest {
    private val documented =
        File(checkNotNull(System.getProperty("mutkt.operatorsDoc")) { "mutkt.operatorsDoc is not set" })
            .readLines()
            .mapNotNull { Regex("^\\| `([a-z-]+)` \\| (yes|no) \\|").find(it)?.groupValues?.get(1) }

    @Test
    fun `every documented operator has a hint and every hint a documented operator`() {
        assertEquals(documented.sorted(), operatorHints.keys.sorted())
    }

    @Test
    fun `hints read as what the tests miss`() {
        assertEquals(
            "no test checks the value exactly at the boundary",
            operatorHint(OperatorId("conditionals-boundary")),
        )
        assertEquals("no test asserts on the returned object", operatorHint(OperatorId("null-returns")))
        assertNull(operatorHint(OperatorId("unknown-operator")))
    }
}
