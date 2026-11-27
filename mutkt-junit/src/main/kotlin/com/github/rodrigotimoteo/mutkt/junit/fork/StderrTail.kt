package com.github.rodrigotimoteo.mutkt.junit.fork

import java.io.ByteArrayOutputStream

internal fun StderrTail.shown(): String = text().ifBlank { "<empty>" }

// Keeps the last [limit] bytes; the buffer is trimmed at twice that to avoid copying on every append.
internal class StderrTail(
    private val limit: Int,
) {
    private val buffer = ByteArrayOutputStream()

    @Synchronized
    fun append(
        bytes: ByteArray,
        length: Int,
    ) {
        buffer.write(bytes, 0, length)
        if (buffer.size() > 2 * limit) {
            val all = buffer.toByteArray()
            buffer.reset()
            buffer.write(all, all.size - limit, limit)
        }
    }

    @Synchronized
    fun text(): String {
        val all = buffer.toByteArray()
        return String(all, maxOf(0, all.size - limit), minOf(all.size, limit), Charsets.UTF_8).trim()
    }
}
