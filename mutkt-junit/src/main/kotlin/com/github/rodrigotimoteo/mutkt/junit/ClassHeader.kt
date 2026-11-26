package com.github.rodrigotimoteo.mutkt.junit

import java.io.ByteArrayInputStream
import java.io.DataInputStream

/** A class file's access flags and the internal name of its superclass (null for java.lang.Object). */
internal data class ClassHeader(
    val access: Int,
    val superName: String?,
)

private const val UTF8 = 1
private const val LONG = 5
private const val DOUBLE = 6
private const val CLASS = 7
private const val MAGIC_AND_VERSION_BYTES = 8L
private const val WIDE_ENTRY_BYTES = 8L

// Sizes of the other constant pool entries after their tag (JVMS 4.4).
@Suppress("MagicNumber")
private val ENTRY_BYTES =
    mapOf(
        3 to 4L,
        4 to 4L,
        8 to 2L,
        9 to 4L,
        10 to 4L,
        11 to 4L,
        12 to 4L,
        15 to 3L,
        16 to 2L,
        17 to 4L,
        18 to 4L,
        19 to 2L,
        20 to 2L,
    )

// Read by hand: this module has no bytecode library and only needs the header.
internal fun classHeader(bytes: ByteArray): ClassHeader {
    val input = DataInputStream(ByteArrayInputStream(bytes))
    input.skipNBytes(MAGIC_AND_VERSION_BYTES)
    val count = input.readUnsignedShort()
    val utf8 = arrayOfNulls<String>(count)
    val classNames = IntArray(count)
    var index = 1
    while (index < count) {
        when (val tag = input.readUnsignedByte()) {
            UTF8 -> utf8[index] = input.readUTF()
            CLASS -> classNames[index] = input.readUnsignedShort()
            // Long and double entries take two pool slots.
            LONG, DOUBLE -> input.skipNBytes(WIDE_ENTRY_BYTES).also { index++ }
            else -> input.skipNBytes(requireNotNull(ENTRY_BYTES[tag]) { "Unknown constant pool tag $tag" })
        }
        index++
    }
    val access = input.readUnsignedShort()
    // this_class, then super_class.
    input.readUnsignedShort()
    return ClassHeader(access, utf8[classNames[input.readUnsignedShort()]])
}
