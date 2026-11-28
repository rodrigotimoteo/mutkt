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
 * This coverage when it encodes to at most [limit] bytes and so fits in a Result frame, else the same per test class
 * only, else null: a baseline without coverage leaves the analysis running every test per mutant instead of failing it.
 */
internal fun Coverage.fitting(limit: Int = MAX_FRAME_BYTES - RESULT_HEADROOM): Coverage? =
    sequenceOf(this, byClass()).firstOrNull { it.encodedSize() <= limit }

// DataOutputStream counts what it writes; the bytes themselves are discarded.
private fun Coverage.encodedSize(): Int =
    DataOutputStream(OutputStream.nullOutputStream()).apply { writeCoverage(this@encodedSize) }.size()

// Each class with its methods' hits and reached classes; its time already includes theirs.
private fun Coverage.byClass(): Coverage {
    val methods = tests.filterNot { it.test.isClass }.groupBy { it.test.className }
    val classes =
        tests.filter { it.test.isClass }.map { test ->
            val own = methods[test.test.className].orEmpty()
            test.copy(hits = test.hits + own.flatMap { it.hits }, reached = test.reached + own.flatMap { it.reached })
        }
    return copy(tests = classes)
}

// Class names go once into a table, each test's hits are a bitset per class and its reached classes are indexes into
// the table, so even a suite of a thousand test classes reaching a thousand probed classes stays far under
// MAX_FRAME_BYTES.
internal fun DataOutputStream.writeCoverage(coverage: Coverage) {
    val hit = (coverage.global + coverage.tests.flatMap { it.hits }).map { it.className }
    val names = (hit + coverage.globalReach + coverage.tests.flatMap { it.reached }).distinct()
    val index = names.withIndex().associate { it.value to it.index }
    writeList(names) { writeString(it) }
    writeHits(coverage.global, index)
    writeList(coverage.globalReach) { writeInt(index.getValue(it)) }
    writeList(coverage.tests) {
        writeTestId(it.test)
        writeLong(it.elapsed.inWholeNanoseconds)
        writeHits(it.hits, index)
        writeList(it.reached) { name -> writeInt(index.getValue(name)) }
    }
}

internal fun DataInputStream.readCoverage(): Coverage {
    val names = readList { readString() }
    val global = readHits(names)
    val globalReach = readList { names.className(readInt()) }.toSet()
    val tests =
        readList {
            val test = readTestId()
            val elapsed = readLong().nanoseconds
            val hits = readHits(names)
            TestCoverage(test, hits, elapsed, readList { names.className(readInt()) }.toSet())
        }
    return Coverage(tests, global, globalReach)
}

internal fun DataOutputStream.writeTestId(test: TestId) {
    writeString(test.className)
    writeOptional(test.method) { writeString(it) }
}

internal fun DataInputStream.readTestId(): TestId = TestId(readString(), readOptional { readString() })

private fun DataOutputStream.writeHits(
    hits: Set<Probe>,
    index: Map<String, Int>,
) = writeList(hits.groupBy { it.className }.entries) { (name, probes) ->
    writeInt(index.getValue(name))
    writeByteArray(BitSet().apply { probes.forEach { set(it.index) } }.toByteArray())
}

private fun DataInputStream.readHits(names: List<String>): Set<Probe> =
    readList {
        val name = names.className(readInt())
        val bits = BitSet.valueOf(readByteArray())
        bits.stream().toArray().map { Probe(name, it) }
    }.flatten().toSet()

private fun List<String>.className(index: Int): String =
    getOrNull(index) ?: throw ForkProtocolException("Class index outside the coverage table")
