package com.github.rodrigotimoteo.mutkt.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Hex SHA-256 of a file's bytes, or of a directory's file paths and contents, each file hashed by [files]; a missing
 * path has a fixed digest.
 */
internal fun contentDigest(
    path: Path,
    files: (Path) -> String = ::fileDigest,
): String =
    when {
        Files.isRegularFile(path) -> files(path)
        Files.isDirectory(path) -> directoryDigest(path, files)
        else -> sha256(listOf(MISSING))
    }

/** Hex SHA-256 of [bytes]. */
internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance(SHA_256).digest(bytes).toHex()

private fun directoryDigest(
    root: Path,
    digest: (Path) -> String,
): String {
    val files = Files.walk(root).use { paths -> paths.filter(Files::isRegularFile).map(root::relativize).toList() }
    val names = files.associateBy { it.joinToString("/") }.toSortedMap()
    return sha256(names.flatMap { (name, file) -> listOf(name, digest(root.resolve(file))) })
}

/** Hex SHA-256 of [file]'s bytes, read in chunks. */
internal fun fileDigest(file: Path): String {
    val digest = MessageDigest.getInstance(SHA_256)
    Files.newInputStream(file).use { input ->
        val buffer = ByteArray(BUFFER)
        generateSequence { input.read(buffer).takeIf { it >= 0 } }.forEach { digest.update(buffer, 0, it) }
    }
    return digest.digest().toHex()
}

// Each part is length-prefixed, so a path cannot run into the next digest.
private fun sha256(parts: List<String>): String {
    val digest = MessageDigest.getInstance(SHA_256)
    for (bytes in parts.map(String::toByteArray)) {
        digest.update("${bytes.size}:".toByteArray())
        digest.update(bytes)
    }
    return digest.digest().toHex()
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private const val SHA_256 = "SHA-256"
private const val BUFFER = 64 * 1024
private const val MISSING = "missing"
