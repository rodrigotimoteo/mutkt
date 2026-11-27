package com.github.rodrigotimoteo.mutkt.junit.fork

import java.io.DataInputStream
import java.io.DataOutputStream

internal fun <T : Any> DataOutputStream.writeOptional(
    value: T?,
    writeValue: DataOutputStream.(T) -> Unit,
) {
    writeBoolean(value != null)
    value?.let { writeValue(it) }
}

internal fun <T> DataInputStream.readOptional(readValue: DataInputStream.() -> T): T? =
    if (readBoolean()) readValue() else null

internal fun <T> DataOutputStream.writeList(
    values: Collection<T>,
    writeElement: DataOutputStream.(T) -> Unit,
) {
    writeInt(values.size)
    values.forEach { writeElement(it) }
}

// Every element takes at least one byte, so a count beyond what the frame holds is corrupt.
internal fun <T> DataInputStream.readList(readElement: DataInputStream.() -> T): List<T> {
    val count = readInt()
    if (count < 0 || count > available()) throw ForkProtocolException("Invalid count $count inside frame")
    return List(count) { readElement() }
}
