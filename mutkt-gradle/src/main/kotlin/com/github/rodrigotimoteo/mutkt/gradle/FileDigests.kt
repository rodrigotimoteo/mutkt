package com.github.rodrigotimoteo.mutkt.gradle

import java.nio.file.Files
import java.nio.file.Path

/** A file's [size], modification time in milliseconds and content [digest]. */
internal data class StampedDigest(
    val size: Long,
    val modified: Long,
    val digest: String,
)

/**
 * File digests by absolute path that reuse a [known] digest while the file's size and modification time are unchanged,
 * so an unchanged classpath is not read again; [used] holds every file asked for, for the next run.
 */
internal class FileDigests(
    private val known: Map<String, StampedDigest>,
) {
    private val used = LinkedHashMap<String, StampedDigest>()

    fun digest(file: Path): String {
        val path = file.toAbsolutePath().toString()
        val size = Files.size(file)
        val modified = Files.getLastModifiedTime(file).toMillis()
        val stamped =
            known[path]?.takeIf { it.size == size && it.modified == modified }
                ?: StampedDigest(size, modified, fileDigest(file))
        used[path] = stamped
        return stamped.digest
    }

    fun used(): Map<String, StampedDigest> = used.toMap()
}
