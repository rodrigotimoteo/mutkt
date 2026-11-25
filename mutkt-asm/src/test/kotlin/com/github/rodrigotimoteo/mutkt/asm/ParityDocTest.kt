package com.github.rodrigotimoteo.mutkt.asm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class ParityDocTest {
    @Test
    fun `documented deviation ids are exactly the ones in code`() {
        val doc = File(checkNotNull(System.getProperty("mutkt.parityDoc")) { "mutkt.parityDoc is not set" })
        val documented =
            doc
                .readLines()
                .mapNotNull { Regex("^\\| (D\\d+[a-z]?) \\|").find(it)?.groupValues?.get(1) }
                .toSet()

        assertEquals(Deviations.all.map { it.id }.toSet(), documented, "docs/parity.md and Deviations.kt disagree")
    }
}
