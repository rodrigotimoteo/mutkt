package com.github.rodrigotimoteo.mutkt.asm

/** One site compared across engines: [line] -1 means unknown (legacy operators that record no line). */
internal data class Row(
    val operator: String,
    val className: String,
    val method: String,
    val line: Int,
    val opcode: String,
) {
    fun sameSiteIgnoringLine(other: Row) = copy(line = other.line) == other
}

/**
 * The committed legacy 0.3.3 site table: [rows] already carry new operator ids.
 *
 * Only sites are compared, so the header's class hashes are diagnostics (see [ParityTest]), not a gate: they differ
 * when the Kotlin or stdlib that compiles the fixtures differs from the tool's.
 */
internal class LegacyTable(
    val classHashes: Map<String, Map<String, String>>,
    val operatorMap: Map<String, String>,
    val rows: List<Row>,
)

internal fun readLegacyTable(): LegacyTable {
    val lines =
        checkNotNull(LegacyTable::class.java.getResourceAsStream("/parity/legacy-0.3.3.tsv")) {
            "missing parity/legacy-0.3.3.tsv"
        }.bufferedReader().use { it.readLines() }
    val header = lines.filter { it.startsWith("# ") }.map { it.removePrefix("# ").split(' ') }
    val hashes =
        header
            .filter { it[0] == "sha256" }
            .groupBy({ it[1] }, { it[2] to it[3] })
            .mapValues { it.value.toMap() }
    val operatorMap = header.filter { it[0] == "map" }.associate { it[1] to it[2] }
    val legacyRows =
        lines.filterNot { it.startsWith("#") || it.isBlank() }.map { line ->
            val c = line.split('\t')
            Row(c[0], c[1], c[2], c[3].toInt(), c[4])
        }
    return LegacyTable(hashes, operatorMap, legacyRows.map { it.copy(operator = operatorMap.getValue(it.operator)) })
}
