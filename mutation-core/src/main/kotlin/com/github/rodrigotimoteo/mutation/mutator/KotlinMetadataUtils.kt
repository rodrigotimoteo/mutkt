package com.github.rodrigotimoteo.mutation.mutator

/**
 * Utilities for parsing Kotlin's @kotlin.Metadata annotation.
 *
 * The annotation's `d1` byte array contains a serialized protobuf of
 * `kotlinx.metadata.jvm.KotlinClassMetadata.Class`. The first field of
 * that message is `flags` (varint), where bit 4 (0x10) indicates a data class.
 *
 * We only need the `flags` field — full protobuf parsing is unnecessary.
 */
internal object KotlinMetadataUtils {
    // Bit 4 of the Class.flags field — set when the class is a `data class`.
    private const val IS_DATA_FLAG: Int = 0x10

    /**
     * Returns true if the @kotlin.Metadata d1 blob indicates a data class.
     * Returns false when d1 is null, empty, or not a recognizable data class metadata.
     */
    fun isKotlinDataClass(d1: ByteArray?): Boolean {
        if (d1 == null || d1.size < 4) return false

        // Outer KotlinClassMetadata: field 1 (Class), wire type LEN (2) -> tag 0x0A
        if (d1[0] != 0x0A.toByte()) return false

        // Read length varint of the embedded Class message
        val (length, lengthSize) = readVarint(d1, 1)
        if (lengthSize == 0 || length <= 0) return false
        val classStart = 1 + lengthSize
        val classEnd = classStart + length
        if (classEnd > d1.size) return false

        // Inside Class: first field is `flags` (field 1, VARINT) -> tag 0x08
        if (d1[classStart] != 0x08.toByte()) return false

        val (flag, _) = readVarint(d1, classStart + 1)
        return (flag and IS_DATA_FLAG) != 0
    }

    /**
     * Decode a base-128 varint starting at [start]. Returns (value, bytesConsumed).
     * Returns (0, 0) on overflow or truncation.
     */
    private fun readVarint(
        bytes: ByteArray,
        start: Int,
    ): Pair<Int, Int> {
        var value = 0
        var shift = 0
        var i = start
        while (i < bytes.size && shift < 32) {
            val b = bytes[i].toInt() and 0xFF
            value = value or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) {
                return value to (i - start + 1)
            }
            shift += 7
            i++
        }
        return 0 to 0
    }
}
