package com.github.rodrigotimoteo.mutkt.asm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

private class Difference(
    val legacyOnly: List<Row>,
    val newOnly: List<Row>,
)

private fun difference(
    legacy: List<Row>,
    new: List<Row>,
): Difference {
    val remainingNew = new.groupingBy { it }.eachCount().toMutableMap()
    val unmatchedLegacy =
        legacy.filter { row ->
            val count = remainingNew[row] ?: 0
            if (count > 0) remainingNew[row] = count - 1
            count == 0
        }
    val newLeft = remainingNew.flatMap { (row, count) -> List(count) { row } }.toMutableList()
    // Legacy rows without a line match a new row of the same site on any line.
    val legacyOnly =
        unmatchedLegacy.filterNot { row ->
            row.line == -1 &&
                newLeft.indexOfFirst { it.sameSiteIgnoringLine(row) }.also { if (it >= 0) newLeft.removeAt(it) } >= 0
        }
    return Difference(legacyOnly, newLeft)
}

private val table by lazy { readLegacyTable() }

private val PROBABLE_CAUSE by lazy {
    val actual = parityClasses.mapValues { (_, classes) -> classes.mapValues { sha256(it.value) } }
    // ParityClasses is known to differ (stdlib version, see LegacyTable), so naming it would only mislead.
    val changed =
        table.classHashes
            .flatMap { (f, classes) -> classes.filter { actual[f]?.get(it.key) != it.value }.keys }
            .filterNot { it == "ParityClasses" }
    if (changed.isEmpty()) {
        ""
    } else {
        "probable cause: fixture bytes differ from the legacy table: $changed; regenerate with tools/parity-legacy"
    }
}

private val differences: Map<String, Difference> by lazy {
    Operators.all.filter { it.id.value in table.operatorMap.values }.associate { op ->
        op.id.value to difference(table.rows.filter { it.operator == op.id.value }, newRows(op))
    }
}

private fun unexplained(
    operator: String,
    side: Side,
    rows: List<Row>,
) = rows.filter { row -> Deviations.all.none { it.operator == operator && it.side == side && it.matches(row) } }

class ParityTest {
    @Test
    fun `every mapped legacy operator exists in the engine`() {
        assertEquals(table.operatorMap.values.toSet(), differences.keys)
    }

    @ParameterizedTest
    @MethodSource("operators")
    fun `every difference to legacy is a documented deviation`(operator: String) {
        val diff = differences.getValue(operator)
        val legacyOnly = unexplained(operator, Side.LEGACY_ONLY, diff.legacyOnly)
        val newOnly = unexplained(operator, Side.NEW_ONLY, diff.newOnly)
        val message =
            (legacyOnly.map { "legacy only: $it" } + newOnly.map { "new only:    $it" })
                .joinToString("\n", "$operator\n", "\n$PROBABLE_CAUSE")
        assertTrue(legacyOnly.isEmpty() && newOnly.isEmpty(), message)
    }

    @Test
    fun `every deviation explains at least one difference`() {
        val unused =
            Deviations.all.filter { d ->
                val diff = differences.getValue(d.operator)
                (if (d.side == Side.LEGACY_ONLY) diff.legacyOnly else diff.newOnly).none(d.matches)
            }
        assertTrue(unused.isEmpty(), "deviations matching no row: ${unused.map { it.id }}")
    }

    @Test
    fun `deviation ids are unique`() {
        assertEquals(
            Deviations.all.size,
            Deviations.all
                .map { it.id }
                .toSet()
                .size,
        )
    }

    @Test
    fun `legacy operators without a new counterpart are not yet implemented`() {
        assertEquals(setOf("COROUTINE", "DATA_CLASS_COPY"), table.notImplemented)
    }

    companion object {
        @JvmStatic
        fun operators(): List<String> = table.operatorMap.values.sorted()
    }
}
