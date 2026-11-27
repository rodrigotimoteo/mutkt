package com.github.rodrigotimoteo.mutkt.junit.fork

import com.github.rodrigotimoteo.mutkt.application.port.Coverage
import com.github.rodrigotimoteo.mutkt.application.port.Probe
import com.github.rodrigotimoteo.mutkt.application.port.TestCoverage
import com.github.rodrigotimoteo.mutkt.application.port.TestId
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.BitSet
import kotlin.time.Duration.Companion.nanoseconds

// Room in a frame for the rest of a Result: the outcome (label and cause capped at 200 chars) and a few numbers.
private const val RESULT_HEADROOM = 64 * 1024

/**
 * This coverage, or null when it encodes to more than [limit] bytes and so would not fit in a Result frame: a baseline
 * without coverage leaves the analysis running every test per mutant instead of failing it.
 */
internal fun Coverage.fitting(limit: Int = MAX_FRAME_BYTES - RESULT_HEADROOM): Coverage? {
    // DataOutputStream counts what it writes; the bytes themselves are discarded.
    val counter = DataOutputStream(OutputStream.nullOutputStream()).apply { writeCoverage(this@fitting) }
    return takeIf { counter.size() <= limit }
}

// Class names go once into a table and each test's hits are a bitset per class, so even a suite of a thousand test
// classes over a thousand probed classes stays far under MAX_FRAME_BYTES.
internal fun DataOutputStream.writeCoverage(coverage: Coverage) {
    val names = (coverage.global + coverage.tests.flatMap { it.hits }).map { it.className }.distinct()
    val index = names.withIndex().associate { it.value to it.index }
    writeList(names) { writeString(it) }
    writeHits(coverage.global, index)
    writeList(coverage.tests) {
        writeString(it.test.className)
        writeLong(it.elapsed.inWholeNanoseconds)
        writeHits(it.hits, index)
    }
}

internal fun DataInputStream.readCoverage(): Coverage {
    val names = readList { readString() }
    val global = readHits(names)
    val tests =
        readList {
            val test = TestId(readString())
            val elapsed = readLong().nanoseconds
            TestCoverage(test, readHits(names), elapsed)
        }
    return Coverage(tests, global)
}

private fun DataOutputStream.writeHits(
    hits: Set<Probe>,
    index: Map<String, Int>,
) = writeList(hits.groupBy { it.className }.entries) { (name, probes) ->
    writeInt(index.getValue(name))
    writeByteArray(BitSet().apply { probes.forEach { set(it.index) } }.toByteArray())
}

private fun DataInputStream.readHits(names: List<String>): Set<Probe> =
    readList {
        val name = names.getOrNull(readInt()) ?: throw ForkProtocolException("Class index outside the coverage table")
        val bits = BitSet.valueOf(readByteArray())
        bits.stream().toArray().map { Probe(name, it) }
    }.flatten().toSet()
